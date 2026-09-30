package com.martpick.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

	@Autowired
	private ProductService productService;

	@Autowired
	private ProductRepository productRepository;

	@BeforeEach
	void setUp() {
		productRepository.save(new Product(CUP_ID, "마트픽테스트 컵라면(110g)", "110", "G"));
		productRepository.save(new Product(MILK_ID, "마트픽테스트 흰우유(1L)", "1000", "ML"));
		productRepository.save(new Product(OIL_ID, "마트픽테스트 식용유(900ml)", "900", "ML"));
		productRepository.flush();
	}

	@Test
	void 검색어가_없으면_전체_상품을_상품_ID_순으로_반환한다() {
		for (String keyword : new String[] {null, "", "   "}) {
			List<ProductView> products = productService.search(keyword);

			assertThat(products).hasSize((int) productRepository.count());
			assertThat(products).extracting(ProductView::goodId).isSorted();
			assertThat(products).extracting(ProductView::goodId).contains(CUP_ID, MILK_ID, OIL_ID);
		}
	}

	@Test
	void 상품명_부분_일치로_검색한다() {
		assertThat(productService.search("테스트 흰우"))
				.extracting(ProductView::goodId)
				.containsExactly(MILK_ID);
	}

	@Test
	void 검색어_앞뒤_공백을_제거한다() {
		assertThat(productService.search("  마트픽테스트 컵라면  "))
				.extracting(ProductView::goodId)
				.containsExactly(CUP_ID);
	}

	@Test
	void 영문_대소문자를_구분하지_않는다() {
		assertThat(productService.search("마트픽테스트 식용유(900ML)"))
				.extracting(ProductView::goodId)
				.containsExactly(OIL_ID);
	}

	@Test
	void 여러_상품이_일치하면_상품_ID_순으로_반환한다() {
		assertThat(productService.search("마트픽테스트"))
				.extracting(ProductView::goodId)
				.containsExactly(MILK_ID, OIL_ID, CUP_ID);
	}

	@Test
	void 일치하는_상품이_없으면_빈_목록을_반환한다() {
		assertThat(productService.search("마트픽테스트 없는상품")).isEmpty();
	}

	@Test
	void LIKE_와일드카드_문자는_문자_그대로_검색한다() {
		assertThat(productService.search("마트픽테스트%")).isEmpty();
		assertThat(productService.search("마트픽테스트_컵라면")).isEmpty();
	}
}
