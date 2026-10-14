package com.martpick.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 공백 차이를 허용하는 상품 검색(PROJECT_SPEC 2-1)을 실제 PostgreSQL에서 확인한다.
 * 상품명은 실제 수집 데이터의 이름 모양을 따르되 테스트 전용 ID 범위만 단언해, 테스트 DB에 다른 상품이 있어도 영향받지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ProductService.class)
class ProductSearchTest {

	private static final long MILK_ID = 9_100_001L;
	private static final long LOW_FAT_MILK_ID = 9_100_002L;
	private static final long CHEESE_ID = 9_100_003L;
	private static final long RAMEN_MILD_ID = 9_100_004L;
	private static final long RAMEN_SPICY_ID = 9_100_005L;
	private static final long RAMEN_SINGLE_ID = 9_100_006L;
	private static final long HATBAN_ID = 9_100_007L;
	private static final long SAUCE_PERCENT_ID = 9_100_008L;
	private static final long LOW_FAT_PERCENT_ID = 9_100_009L;
	private static final long LATIN_ID = 9_100_010L;
	private static final long PAGED_FIRST_ID = 9_100_100L;
	private static final int PAGED_COUNT = 65;

	@Autowired
	private ProductService productService;

	@Autowired
	private ProductRepository productRepository;

	@BeforeEach
	void setUp() {
		save(MILK_ID, "서울우유 흰우유(1L)");
		save(LOW_FAT_MILK_ID, "서울우유 저지방 우유(1L)");
		save(CHEESE_ID, "서울우유 체다슬라이스치즈(270g)");
		save(RAMEN_MILD_ID, "진라면 순한맛(5개입)");
		save(RAMEN_SPICY_ID, "진라면 매운맛(5개입)");
		save(RAMEN_SINGLE_ID, "삼양라면(5개입)");
		save(HATBAN_ID, "CJ 햇반 210g");
		save(SAUCE_PERCENT_ID, "순창 100% 현미 태양초 찰고추장(1kg)");
		save(LOW_FAT_PERCENT_ID, "매일우유 저지방 1%(900ml)");
		save(LATIN_ID, "Pulmuone Hat Ban Set");
		LongStream.range(0, PAGED_COUNT).forEach(i -> save(PAGED_FIRST_ID + i, "마트픽페이징 상품 " + i));
		productRepository.flush();
	}

	private void save(long goodId, String name) {
		productRepository.save(new Product(goodId, name, "1", "EA"));
	}

	private List<Long> ids(String keyword) {
		return testIds(productService.search(keyword, 1));
	}

	private static List<Long> testIds(ProductPage page) {
		return page.products().stream().map(ProductView::goodId).filter(id -> id >= 9_100_000L && id < 9_200_000L).toList();
	}

	@Test
	void 검색어의_공백_유무와_관계없이_같은_상품을_찾는다() {
		assertThat(ids("서울우유 흰우유")).containsExactly(MILK_ID);
		assertThat(ids("서울우유흰우유")).containsExactly(MILK_ID);
		assertThat(ids("서울우유  흰우유")).containsExactly(MILK_ID);
		assertThat(ids("서울 우유 흰 우유")).containsExactly(MILK_ID);
	}

	@Test
	void 각_토큰이_상품명_전체에_모두_들어_있으면_찾는다() {
		assertThat(ids("우유 1L")).containsExactly(MILK_ID, LOW_FAT_MILK_ID);
		assertThat(ids("라면 5개입")).containsExactly(RAMEN_MILD_ID, RAMEN_SPICY_ID, RAMEN_SINGLE_ID);
		assertThat(ids("5개입 라면")).containsExactly(RAMEN_MILD_ID, RAMEN_SPICY_ID, RAMEN_SINGLE_ID);
	}

	@Test
	void 붙여_쓴_검색어와_띄어_쓴_검색어가_상품명의_공백과_달라도_찾는다() {
		assertThat(ids("진라면순한맛")).containsExactly(RAMEN_MILD_ID);
		assertThat(ids("진 라면")).containsExactly(RAMEN_MILD_ID, RAMEN_SPICY_ID);
		assertThat(ids("햇 반")).containsExactly(HATBAN_ID);
		assertThat(ids("햇반")).containsExactly(HATBAN_ID);
		assertThat(ids("햇반210g")).containsExactly(HATBAN_ID);
	}

	@Test
	void 여러_토큰_중_일부만_일치하면_찾지_않는다() {
		assertThat(ids("서울우유 라면")).isEmpty();
		assertThat(ids("우유 존재하지않는토큰")).isEmpty();
		assertThat(ids("진라면 순한맛 매운맛")).isEmpty();
	}

	@Test
	void 대소문자를_구분하지_않는다() {
		assertThat(ids("hat ban")).containsExactly(LATIN_ID);
		assertThat(ids("HAT BAN")).containsExactly(LATIN_ID);
		assertThat(ids("pulmuone hatban set")).containsExactly(LATIN_ID);
		assertThat(ids("cj 햇반 210G")).containsExactly(HATBAN_ID);
	}

	@Test
	void 전각_공백과_연속_공백도_구분자로_본다() {
		assertThat(ids("서울우유　흰우유")).containsExactly(MILK_ID);
		assertThat(ids("서울우유 흰우유")).containsExactly(MILK_ID);
		assertThat(ids("  서울우유     흰우유  ")).containsExactly(MILK_ID);
	}

	@Test
	void 퍼센트와_밑줄은_와일드카드가_아니라_문자로_찾는다() {
		assertThat(ids("%")).containsExactly(SAUCE_PERCENT_ID, LOW_FAT_PERCENT_ID);
		assertThat(ids("100%")).containsExactly(SAUCE_PERCENT_ID);
		assertThat(ids("1%")).containsExactly(LOW_FAT_PERCENT_ID);
		assertThat(ids("_")).isEmpty();
		assertThat(ids("우_유")).isEmpty();
		assertThat(ids("우%유")).isEmpty();
		assertThat(ids("\\")).isEmpty();
		assertThat(ids("% %")).containsExactly(SAUCE_PERCENT_ID, LOW_FAT_PERCENT_ID);
		assertThat(ids("% _")).isEmpty();
	}

	@Test
	void SQL_조각이_들어간_검색어는_문자로만_취급한다() {
		long before = productRepository.count();

		assertThat(ids("'; drop table product; --")).isEmpty();
		assertThat(ids("' or '1'='1")).isEmpty();
		assertThat(ids("햇반' or 1=1 --")).isEmpty();

		assertThat(productRepository.count()).isEqualTo(before);
	}

	@Test
	void 빈_검색어와_공백뿐인_검색어는_전체_상품을_조회한다() {
		long total = productRepository.count();

		for (String keyword : new String[] {null, "", " ", "   ", "\t \n"}) {
			assertThat(productService.search(keyword, 1).totalCount()).isEqualTo(total);
		}
	}

	@Test
	void 앞뒤_공백을_제거해도_같은_결과이고_같은_토큰이_반복되어도_결과는_같다() {
		assertThat(ids("  햇반  ")).isEqualTo(ids("햇반"));
		assertThat(ids("우유 우유 우유")).isEqualTo(ids("우유"));
		assertThat(ids("우유 우유 1L 1L")).isEqualTo(ids("우유 1L"));
	}

	@Test
	void 기존_부분_일치_검색으로_찾던_상품은_빠지지_않는다() {
		List<String> keywords = List.of("서울우유", "우유", "흰우유(1L)", "서울우유 흰우유(1L)", "서울우유 저지방", "진라면 순한맛(5개입)",
				"CJ 햇반 210g", "cj 햇반", "Hat Ban", "100% 현미", "1%(900ml)", "상품 1", "마트픽페이징 상품 3");
		for (String keyword : keywords) {
			List<Long> before = productRepository.findAll().stream()
					.filter(product -> product.getName().toLowerCase().contains(keyword.toLowerCase()))
					.map(Product::getGoodId).filter(id -> id >= 9_100_000L && id < 9_200_000L).toList();
			assertThat(before).as("기존 검색에 결과가 있어야 비교가 의미 있음: " + keyword).isNotEmpty();
			assertThat(ids(keyword)).as(keyword).containsAll(before);
		}
	}

	@Test
	void 검색_결과도_상품_ID_순으로_30개씩_나눈다() {
		ProductPage first = productService.search("마트픽페이징", 1);
		ProductPage second = productService.search("마트픽페이징", 2);
		ProductPage third = productService.search("마트픽페이징", 3);

		assertThat(first.totalCount()).isEqualTo(PAGED_COUNT);
		assertThat(first.totalPages()).isEqualTo(3);
		assertThat(first.products()).hasSize(30);
		assertThat(second.products()).hasSize(30);
		assertThat(third.products()).hasSize(5);
		assertThat(first.products().get(0).goodId()).isEqualTo(PAGED_FIRST_ID);
		assertThat(second.products().get(0).goodId()).isEqualTo(PAGED_FIRST_ID + 30);
		assertThat(third.products().get(4).goodId()).isEqualTo(PAGED_FIRST_ID + 64);
	}

	@Test
	void 공백이_섞인_검색어의_페이지_경계와_범위_밖_페이지_보정() {
		ProductPage exactBoundary = productService.search("마트픽 페이징 상품", 3);
		ProductPage beyond = productService.search("마트픽 페이징 상품", 99);

		assertThat(exactBoundary.totalCount()).isEqualTo(PAGED_COUNT);
		assertThat(exactBoundary.products()).hasSize(5);
		assertThat(beyond.page()).isEqualTo(3);
		assertThat(beyond.products()).hasSize(5);
		assertThat(productService.search("마트픽 없는토큰", 1).totalPages()).isZero();
	}

	@Test
	void 여러_토큰_결과도_상품_ID_오름차순으로_정렬된다() {
		List<Long> ids = productService.search("우유 L", 1).products().stream().map(ProductView::goodId).toList();

		assertThat(ids).isSorted();
		assertThat(testIds(productService.search("우유 L", 1))).contains(MILK_ID, LOW_FAT_MILK_ID);
	}

	@ParameterizedTest
	@CsvSource({"서울우유 흰우유,1", "서울우유흰우유,1", "우유 1L,2", "진라면순한맛,1", "진 라면,2", "라면 5개입,3", "햇반,1"})
	void 지정한_검색어별_결과_건수(String keyword, int expected) {
		assertThat(ids(keyword)).hasSize(expected);
	}

	@Test
	void 아주_많은_토큰이_있는_검색어도_정상적으로_처리한다() {
		String manyTokens = String.join(" ", java.util.Collections.nCopies(2000, "우유")) + " 흰우유";

		assertThat(ids(manyTokens)).containsExactly(MILK_ID);
		String distinct = LongStream.range(0, 1500).mapToObj(i -> "t" + i).collect(java.util.stream.Collectors.joining(" "));
		assertThat(ids(distinct)).isEmpty();
	}
}
