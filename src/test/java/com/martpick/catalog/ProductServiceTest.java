package com.martpick.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 테스트 DB에 이미 있는 상품과 섞이지 않도록 테스트 전용 ID와 고유한 상품명 접두어를 사용한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ProductService.class)
class ProductServiceTest {

	private static final long CUP_ID = 9_000_003L;
	private static final long MILK_ID = 9_000_001L;
	private static final long OIL_ID = 9_000_002L;
	/** 페이지 테스트용 상품 65개: 30 + 30 + 5 */
	private static final long PAGED_FIRST_ID = 9_000_401L;
	private static final int PAGED_COUNT = 65;

	@Autowired
	private ProductService productService;

	@Autowired
	private ProductRepository productRepository;

	@BeforeEach
	void setUp() {
		productRepository.save(new Product(CUP_ID, "마트픽테스트 컵라면(110g)", "110", "G"));
		productRepository.save(new Product(MILK_ID, "마트픽테스트 흰우유(1L)", "1000", "ML"));
		productRepository.save(new Product(OIL_ID, "마트픽테스트 식용유(900ml)", "900", "ML"));
		LongStream.range(0, PAGED_COUNT).forEach(i -> productRepository.save(
				new Product(PAGED_FIRST_ID + i, "마트픽페이지 상품 " + i, "1", "EA")));
		productRepository.flush();
	}

	@Test
	void 검색어가_없으면_전체_상품을_상품_ID_순으로_30개씩_나눈다() {
		long total = productRepository.count();
		int expectedPages = (int) ((total + 29) / 30);

		for (String keyword : new String[] {null, "", "   "}) {
			ProductPage first = productService.search(keyword, 1);

			assertThat(first.totalCount()).isEqualTo(total);
			assertThat(first.totalPages()).isEqualTo(expectedPages);
			assertThat(first.page()).isEqualTo(1);
			assertThat(first.products()).hasSize(30);
			assertThat(first.products()).extracting(ProductView::goodId).isSorted();
		}
	}

	@Test
	void 검색어가_있어도_결과를_30개씩_나누고_상품_ID_순으로_이어진다() {
		ProductPage first = productService.search("마트픽페이지", 1);
		ProductPage second = productService.search("마트픽페이지", 2);
		ProductPage third = productService.search("마트픽페이지", 3);

		assertThat(first.totalCount()).isEqualTo(PAGED_COUNT);
		assertThat(first.totalPages()).isEqualTo(3);
		assertThat(first.products()).hasSize(30);
		assertThat(second.products()).hasSize(30);
		assertThat(third.products()).hasSize(5);
		List<Long> ids = new ArrayList<>();
		for (ProductPage page : List.of(first, second, third)) {
			page.products().forEach(product -> ids.add(product.goodId()));
		}
		assertThat(ids).containsExactlyElementsOf(
				LongStream.range(0, PAGED_COUNT).mapToObj(i -> PAGED_FIRST_ID + i).toList());
	}

	@Test
	void 첫_페이지보다_작은_번호는_첫_페이지로_보정한다() {
		for (int page : new int[] {0, -1, Integer.MIN_VALUE}) {
			ProductPage result = productService.search("마트픽페이지", page);

			assertThat(result.page()).isEqualTo(1);
			assertThat(result.products().get(0).goodId()).isEqualTo(PAGED_FIRST_ID);
		}
	}

	@Test
	void 전체_페이지_수를_넘는_번호는_마지막_페이지로_보정한다() {
		for (int page : new int[] {4, 99, Integer.MAX_VALUE}) {
			ProductPage result = productService.search("마트픽페이지", page);

			assertThat(result.page()).isEqualTo(3);
			assertThat(result.products()).hasSize(5);
		}
	}

	@Test
	void 검색_결과가_없으면_빈_첫_페이지를_반환한다() {
		for (int page : new int[] {1, 5}) {
			ProductPage result = productService.search("마트픽테스트 없는상품", page);

			assertThat(result.products()).isEmpty();
			assertThat(result.page()).isEqualTo(1);
			assertThat(result.totalPages()).isZero();
			assertThat(result.totalCount()).isZero();
		}
	}

	@Test
	void 상품명_부분_일치로_검색한다() {
		assertThat(productService.search("테스트 흰우", 1).products())
				.extracting(ProductView::goodId)
				.containsExactly(MILK_ID);
	}

	@Test
	void 검색어_앞뒤_공백을_제거한다() {
		assertThat(productService.search("  마트픽테스트 컵라면  ", 1).products())
				.extracting(ProductView::goodId)
				.containsExactly(CUP_ID);
	}

	@Test
	void 영문_대소문자를_구분하지_않는다() {
		assertThat(productService.search("마트픽테스트 식용유(900ML)", 1).products())
				.extracting(ProductView::goodId)
				.containsExactly(OIL_ID);
	}

	@Test
	void 여러_상품이_일치하면_상품_ID_순으로_반환한다() {
		assertThat(productService.search("마트픽테스트", 1).products())
				.extracting(ProductView::goodId)
				.containsExactly(MILK_ID, OIL_ID, CUP_ID);
	}

	@Test
	void LIKE_와일드카드_문자는_문자_그대로_검색한다() {
		assertThat(productService.search("마트픽테스트%", 1).products()).isEmpty();
		assertThat(productService.search("마트픽테스트_컵라면", 1).products()).isEmpty();
	}
}
