package com.martpick.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.martpick.catalog.Price;
import com.martpick.catalog.Product;
import com.martpick.catalog.Region;
import com.martpick.catalog.Store;
import com.martpick.config.MartPickProperties;

import jakarta.persistence.EntityManagerFactory;

/**
 * 테스트 전용 DB(martpick_test)에서 실행하며 각 테스트는 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ComparisonService.class, PriceComparator.class, ComparisonServiceTest.Config.class})
class ComparisonServiceTest {

	private static final LocalDate DAY = LocalDate.of(2026, 9, 25);
	private static final LocalDate OTHER_DAY = LocalDate.of(2026, 9, 18);

	private static final String SEOUL = "020100000";
	private static final String OLD_GWANGJU = "020200000";
	private static final String DAEGU = "020300000";
	private static final String BUSAN = "020500000";
	private static final String INCHEON = "020700000";
	private static final String GYEONGGI = "020900000";

	private static final long RAMEN = 1000L;
	private static final long SOUP = 1003L;
	private static final long RICE_SOUP = 1008L;
	private static final long RICE = 292L;
	private static final long NOT_COLLECTED = 553L;
	/** 어느 판매점에도 가격이 없는 상품 */
	private static final long NO_PRICE = 9_001L;
	/** 비교 제외(본사) 판매점에만 가격이 있는 상품 */
	private static final long HEADQUARTERS_ONLY = 9_002L;
	/** 기준 조사일이 아닌 날에만 가격이 있는 상품 */
	private static final long OTHER_DAY_ONLY = 9_003L;

	@TestConfiguration
	static class Config {

		/** application.yml에서 바인딩되는 설정 대신 테스트 기준 조사일을 명시한다. */
		@Bean
		@Primary
		MartPickProperties testMartPickProperties() {
			return new MartPickProperties(DAY,
					new MartPickProperties.Collect(false, List.of(), List.of(), false, List.of(), 0));
		}
	}

	@Autowired
	private ComparisonService comparisonService;

	@Autowired
	private TestEntityManager em;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void setUp() {
		region(SEOUL, "서울특별시");
		region(OLD_GWANGJU, "(구)광주광역시");
		region(DAEGU, "대구광역시");
		region(BUSAN, "부산광역시");
		region(INCHEON, "인천광역시");
		region(GYEONGGI, "경기도");
		for (long goodId : new long[] {RAMEN, SOUP, RICE_SOUP, RICE}) {
			em.persist(new Product(goodId, "상품" + goodId, "100", "G"));
		}

		// 서울: 완전 비교 1·3, 부분 비교 2 (1008은 다른 조사일에만 가격 있음), 본사 115
		Store store1 = store(1, SEOUL, "서울마트1", "서울 도로명 1", false);
		Store store2 = store(2, SEOUL, "서울마트2", null, false);
		Store store3 = store(3, SEOUL, "서울마트3", "서울 도로명 3", false);
		Store headquarters = store(115, SEOUL, "편의점(본사)", "서울 본사", true);
		prices(store1, DAY, RAMEN, 1000, SOUP, 1000, RICE_SOUP, 1000, RICE, 50);
		prices(store2, DAY, RAMEN, 1200, SOUP, 800);
		prices(store2, OTHER_DAY, RICE_SOUP, 100);
		prices(store3, DAY, RAMEN, 900, SOUP, 700, RICE_SOUP, 900);
		prices(headquarters, DAY, RAMEN, 10, SOUP, 10, RICE_SOUP, 10);

		// 경기: 다른 지역의 더 싼 판매점
		prices(store(10, GYEONGGI, "경기마트", "경기 도로명", false), DAY, RAMEN, 1, SOUP, 1, RICE_SOUP, 1);
		// 인천: 라면 가격만 있는 판매점
		prices(store(20, INCHEON, "인천마트", "인천 도로명", false), DAY, RAMEN, 1500);
		// 부산: 본사만 가격 있음 / 대구: 다른 조사일에만 가격 있음 / (구)광주: 판매점은 있으나 가격 없음
		prices(store(62, BUSAN, "부산편의점(본사)", "부산 본사", true), DAY, RAMEN, 1600);
		prices(store(30, DAEGU, "대구마트", "대구 도로명", false), OTHER_DAY, RAMEN, 1000);
		store(40, OLD_GWANGJU, "광주마트", "광주 도로명", false);

		em.flush();
		em.clear();
	}

	@Test
	void 선택_지역의_완전_비교와_부분_비교_결과에_조사일과_지역을_채운다() {
		ComparisonResult result = comparisonService.compare(SEOUL, cart(RAMEN, 1, SOUP, 1, RICE_SOUP, 1));

		assertThat(result.inspectDay()).isEqualTo(DAY);
		assertThat(result.areaCode()).isEqualTo(SEOUL);
		assertThat(result.areaName()).isEqualTo("서울특별시");
		assertThat(result.status()).isEqualTo(ComparisonStatus.COMPARED);
		assertThat(result.completeStores()).extracting(CompleteStore::entpId, CompleteStore::totalPrice)
				.containsExactly(tuple(3L, 2_500L), tuple(1L, 3_000L));
		assertThat(result.partialStores()).extracting(PartialStore::entpId, PartialStore::knownSubtotal)
				.containsExactly(tuple(2L, 2_000L));
		assertThat(result.completeStores().get(0).items()).extracting(item -> item.product().name())
				.containsExactly("상품1000", "상품1003", "상품1008");
	}

	@Test
	void 다른_조사일_가격은_섞지_않고_누락으로_분류한다() {
		PartialStore store2 = comparisonService.compare(SEOUL, cart(RAMEN, 1, SOUP, 1, RICE_SOUP, 1))
				.partialStores().get(0);

		assertThat(store2.missingProducts()).extracting(ProductInfo::goodId).containsExactly(RICE_SOUP);
		assertThat(store2.pricedItems()).extracting(ItemPrice::unitPrice).containsExactly(1200, 800);
	}

	@Test
	void 다른_지역과_본사_판매점은_결과에_없다() {
		ComparisonResult result = comparisonService.compare(SEOUL, cart(RAMEN, 1, SOUP, 1, RICE_SOUP, 1));

		assertThat(result.completeStores()).extracting(CompleteStore::entpId).doesNotContain(10L, 115L);
		assertThat(result.partialStores()).extracting(PartialStore::entpId).doesNotContain(10L, 115L);
	}

	@Test
	void 장바구니_밖_상품의_가격은_계산에_섞이지_않는다() {
		ComparisonResult result = comparisonService.compare(SEOUL, cart(RAMEN, 1));

		CompleteStore store1 = result.completeStores().stream().filter(s -> s.entpId() == 1L).findFirst().orElseThrow();
		assertThat(store1.items()).extracting(item -> item.product().goodId()).containsExactly(RAMEN);
		assertThat(store1.totalPrice()).isEqualTo(1_000L);
	}

	@Test
	void 수량_경계값_1과_99를_반영한다() {
		ComparisonResult result = comparisonService.compare(SEOUL, cart(RAMEN, 99, SOUP, 1));

		assertThat(result.completeStores()).extracting(CompleteStore::entpId, CompleteStore::totalPrice)
				.containsExactly(tuple(3L, 900L * 99 + 700), tuple(1L, 1000L * 99 + 1000), tuple(2L, 1200L * 99 + 800));
	}

	@Test
	void 부분_비교_판매점만_있으면_NO_COMPLETE_STORE이다() {
		ComparisonResult result = comparisonService.compare(INCHEON, cart(RAMEN, 1, SOUP, 1));

		assertThat(result.status()).isEqualTo(ComparisonStatus.NO_COMPLETE_STORE);
		assertThat(result.partialStores().get(0).knownSubtotal()).isEqualTo(1_500L);
	}

	@Test
	void 비교_가능한_지역이지만_장바구니_상품_가격이_없으면_NO_PRICED_STORE이다() {
		ComparisonResult result = comparisonService.compare(INCHEON, cart(SOUP, 1));

		assertThat(result.status()).isEqualTo(ComparisonStatus.NO_PRICED_STORE);
	}

	@Test
	void 비교_가능한_지역_목록은_기준_조사일에_가격이_있는_비본사_판매점의_지역만_코드순으로_반환한다() {
		assertThat(comparisonService.comparableRegions())
				.extracting(RegionOption::code, RegionOption::name)
				.containsExactly(tuple(SEOUL, "서울특별시"), tuple(INCHEON, "인천광역시"), tuple(GYEONGGI, "경기도"));
	}

	@Test
	void 지역_목록과_비교_요청의_지역_검증은_같은_기준을_쓴다() {
		for (RegionOption region : comparisonService.comparableRegions()) {
			assertThat(comparisonService.compare(region.code(), cart(RAMEN, 1)).areaCode()).isEqualTo(region.code());
		}
		for (String excluded : List.of(OLD_GWANGJU, DAEGU, BUSAN)) {
			assertInvalid(excluded, cart(RAMEN, 1), InvalidComparisonRequestException.Reason.UNKNOWN_REGION);
		}
	}

	@Test
	void 빈_장바구니는_EMPTY_CART이다() {
		assertInvalid(SEOUL, List.of(), InvalidComparisonRequestException.Reason.EMPTY_CART);
		assertInvalid(SEOUL, null, InvalidComparisonRequestException.Reason.EMPTY_CART);
	}

	@Test
	void 장바구니에_null_줄이_있으면_INVALID_CART_LINE이다() {
		assertInvalid(SEOUL, Arrays.asList(new CartLine(RAMEN, 1), null),
				InvalidComparisonRequestException.Reason.INVALID_CART_LINE);
	}

	@Test
	void 지역을_선택하지_않으면_REGION_REQUIRED이다() {
		assertInvalid(null, cart(RAMEN, 1), InvalidComparisonRequestException.Reason.REGION_REQUIRED);
		assertInvalid("  ", cart(RAMEN, 1), InvalidComparisonRequestException.Reason.REGION_REQUIRED);
	}

	@Test
	void 수량이_1_미만이거나_99_초과면_QUANTITY_OUT_OF_RANGE이다() {
		assertInvalid(SEOUL, cart(RAMEN, 0), InvalidComparisonRequestException.Reason.QUANTITY_OUT_OF_RANGE);
		assertInvalid(SEOUL, cart(RAMEN, 100), InvalidComparisonRequestException.Reason.QUANTITY_OUT_OF_RANGE);
	}

	@Test
	void 같은_상품이_두_줄이면_DUPLICATE_PRODUCT이다() {
		assertInvalid(SEOUL, cart(RAMEN, 1, RAMEN, 2), InvalidComparisonRequestException.Reason.DUPLICATE_PRODUCT);
	}

	@Test
	void 비교_가능_목록에_없는_지역은_UNKNOWN_REGION이다() {
		assertInvalid(OLD_GWANGJU, cart(RAMEN, 1), InvalidComparisonRequestException.Reason.UNKNOWN_REGION);
		assertInvalid("999999999", cart(RAMEN, 1), InvalidComparisonRequestException.Reason.UNKNOWN_REGION);
	}

	@Test
	void 수집되지_않은_상품은_UNKNOWN_PRODUCT이다() {
		assertInvalid(SEOUL, cart(RAMEN, 1, NOT_COLLECTED, 1), InvalidComparisonRequestException.Reason.UNKNOWN_PRODUCT);
	}

	@Test
	void 상품_ID가_0이거나_음수면_UNKNOWN_PRODUCT이다() {
		assertInvalid(SEOUL, cart(0L, 1), InvalidComparisonRequestException.Reason.UNKNOWN_PRODUCT);
		assertInvalid(SEOUL, cart(-1L, 1), InvalidComparisonRequestException.Reason.UNKNOWN_PRODUCT);
	}

	@Test
	void 여러_오류가_겹치면_승인된_검증_순서대로_먼저_걸린_사유를_반환한다() {
		assertInvalid(null, List.of(), InvalidComparisonRequestException.Reason.EMPTY_CART);
		assertInvalid(null, Arrays.asList(new CartLine(RAMEN, 0), null), InvalidComparisonRequestException.Reason.INVALID_CART_LINE);
		assertInvalid(null, cart(RAMEN, 0), InvalidComparisonRequestException.Reason.REGION_REQUIRED);
		assertInvalid(OLD_GWANGJU, cart(RAMEN, 0, RAMEN, 1), InvalidComparisonRequestException.Reason.QUANTITY_OUT_OF_RANGE);
		assertInvalid(OLD_GWANGJU, cart(NOT_COLLECTED, 1, NOT_COLLECTED, 1), InvalidComparisonRequestException.Reason.DUPLICATE_PRODUCT);
		assertInvalid(OLD_GWANGJU, cart(NOT_COLLECTED, 1), InvalidComparisonRequestException.Reason.UNKNOWN_REGION);
	}

	@Test
	void 판매점과_상품_수가_늘어도_SQL_실행_수가_늘지_않는다() {
		long small = statementCount(() -> comparisonService.compare(INCHEON, cart(RAMEN, 1)));
		long large = statementCount(() -> comparisonService.compare(SEOUL, cart(RAMEN, 1, SOUP, 2, RICE_SOUP, 3)));

		assertThat(small).as("테스트 프로필에서 Hibernate 통계가 켜져 있어야 한다").isPositive();
		assertThat(large).isEqualTo(small);
		assertThat(large).isLessThanOrEqualTo(3);
	}

	// ---- 가격 데이터 부재 안내 (6-3 U5) ----

	@Test
	void 장바구니_상품에_모두_가격이_있으면_가격_부재_안내_목록은_비어_있다() {
		ComparisonResult result = comparisonService.compare(SEOUL, cart(RAMEN, 1, SOUP, 1, RICE_SOUP, 1));

		assertThat(result.noPriceNationwide()).isEmpty();
		assertThat(result.noPriceInRegion()).isEmpty();
	}

	@Test
	void 기준_조사일에_어느_판매점에도_가격이_없는_상품은_전국_부재로_안내한다() {
		givenProductsWithoutComparablePrice();

		ComparisonResult result = comparisonService.compare(SEOUL, cart(RAMEN, 1, NO_PRICE, 1));

		assertThat(result.noPriceNationwide()).extracting(ProductInfo::goodId).containsExactly(NO_PRICE);
		assertThat(result.noPriceNationwide().get(0).name()).isEqualTo("가격없는상품");
		assertThat(result.noPriceInRegion()).isEmpty();
	}

	@Test
	void 본사_판매점에만_또는_다른_조사일에만_가격이_있는_상품도_비교에_쓸_가격이_없으므로_전국_부재로_본다() {
		givenProductsWithoutComparablePrice();

		ComparisonResult result = comparisonService.compare(SEOUL,
				cart(RAMEN, 1, HEADQUARTERS_ONLY, 1, OTHER_DAY_ONLY, 1));

		assertThat(result.noPriceNationwide()).extracting(ProductInfo::goodId)
				.containsExactly(HEADQUARTERS_ONLY, OTHER_DAY_ONLY);
		assertThat(result.noPriceInRegion()).isEmpty();
	}

	@Test
	void 선택_지역에는_가격이_없지만_다른_지역에_있는_상품은_지역_부재로_안내한다() {
		ComparisonResult result = comparisonService.compare(INCHEON, cart(RAMEN, 1, SOUP, 1));

		assertThat(result.noPriceInRegion()).extracting(ProductInfo::goodId).containsExactly(SOUP);
		assertThat(result.noPriceNationwide()).isEmpty();
		assertThat(result.status()).isEqualTo(ComparisonStatus.NO_COMPLETE_STORE);
	}

	@Test
	void 전국_부재와_지역_부재를_구분하고_각각_장바구니에_담은_순서를_유지한다() {
		givenProductsWithoutComparablePrice();

		ComparisonResult result = comparisonService.compare(INCHEON,
				cart(NO_PRICE, 1, SOUP, 1, RAMEN, 1, RICE_SOUP, 1, HEADQUARTERS_ONLY, 1));

		assertThat(result.noPriceNationwide()).extracting(ProductInfo::goodId).containsExactly(NO_PRICE, HEADQUARTERS_ONLY);
		assertThat(result.noPriceInRegion()).extracting(ProductInfo::goodId).containsExactly(SOUP, RICE_SOUP);
	}

	@Test
	void 장바구니_순서를_바꾸면_안내_목록의_순서도_바뀐다() {
		givenProductsWithoutComparablePrice();

		ComparisonResult result = comparisonService.compare(SEOUL,
				cart(OTHER_DAY_ONLY, 1, RAMEN, 1, HEADQUARTERS_ONLY, 1, NO_PRICE, 1));

		assertThat(result.noPriceNationwide()).extracting(ProductInfo::goodId)
				.containsExactly(OTHER_DAY_ONLY, HEADQUARTERS_ONLY, NO_PRICE);
	}

	@Test
	void 모든_상품에_가격이_없으면_NO_PRICED_STORE이고_상품을_모두_안내한다() {
		givenProductsWithoutComparablePrice();

		ComparisonResult result = comparisonService.compare(SEOUL, cart(NO_PRICE, 1, HEADQUARTERS_ONLY, 2));

		assertThat(result.status()).isEqualTo(ComparisonStatus.NO_PRICED_STORE);
		assertThat(result.completeStores()).isEmpty();
		assertThat(result.partialStores()).isEmpty();
		assertThat(result.noPriceNationwide()).extracting(ProductInfo::goodId).containsExactly(NO_PRICE, HEADQUARTERS_ONLY);
	}

	@Test
	void 가격_없는_상품이_있어도_자동_제외하지_않고_부분_비교의_누락_상품으로_남는다() {
		givenProductsWithoutComparablePrice();

		ComparisonResult result = comparisonService.compare(SEOUL, cart(RAMEN, 1, NO_PRICE, 1));

		assertThat(result.completeStores()).isEmpty();
		assertThat(result.status()).isEqualTo(ComparisonStatus.NO_COMPLETE_STORE);
		assertThat(result.partialStores()).extracting(PartialStore::entpId, PartialStore::knownSubtotal)
				.containsExactly(tuple(1L, 1_000L), tuple(2L, 1_200L), tuple(3L, 900L));
		assertThat(result.partialStores()).allSatisfy(store -> assertThat(store.missingProducts())
				.extracting(ProductInfo::goodId).containsExactly(NO_PRICE));
	}

	@Test
	void 가격_부재_안내는_기존_완전_비교_부분_비교_계산을_바꾸지_않는다() {
		givenProductsWithoutComparablePrice();
		ComparisonResult before = comparisonService.compare(SEOUL, cart(RAMEN, 1, SOUP, 1));

		ComparisonResult after = comparisonService.compare(SEOUL, cart(RAMEN, 1, SOUP, 1, NO_PRICE, 1));

		assertThat(before.completeStores()).extracting(CompleteStore::entpId).containsExactly(3L, 1L, 2L);
		assertThat(after.completeStores()).isEmpty();
		List<Tuple> expectedSubtotals = before.completeStores().stream()
				.sorted(Comparator.comparingLong(CompleteStore::entpId))
				.map(store -> tuple(store.entpId(), store.totalPrice()))
				.toList();
		assertThat(after.partialStores()).extracting(PartialStore::entpId, PartialStore::knownSubtotal)
				.containsExactlyElementsOf(expectedSubtotals);
		assertThat(before.noPriceNationwide()).isEmpty();
	}

	@Test
	void 가격_부재가_있어도_SQL은_한_번만_늘고_부재_상품_수와_관계없다() {
		givenProductsWithoutComparablePrice();

		long allPriced = statementCount(() -> comparisonService.compare(SEOUL, cart(RAMEN, 1, SOUP, 1)));
		long oneMissing = statementCount(() -> comparisonService.compare(SEOUL, cart(RAMEN, 1, NO_PRICE, 1)));
		long manyMissing = statementCount(() -> comparisonService.compare(SEOUL,
				cart(RAMEN, 1, NO_PRICE, 1, HEADQUARTERS_ONLY, 1, OTHER_DAY_ONLY, 1)));

		assertThat(allPriced).as("테스트 프로필에서 Hibernate 통계가 켜져 있어야 한다").isPositive();
		assertThat(oneMissing).isEqualTo(allPriced + 1);
		assertThat(manyMissing).isEqualTo(oneMissing);
	}

	// ---- 장바구니의 전국 가격 부재 조회 (6-3 U6) ----

	@Test
	void 가격이_있는_상품만_담았으면_가격_없는_상품_집합은_비어_있다() {
		assertThat(comparisonService.goodIdsWithoutPrice(List.of(RAMEN, SOUP, RICE_SOUP, RICE))).isEmpty();
	}

	@Test
	void 비교에_쓸_가격이_없는_상품만_담긴_순서대로_돌려준다() {
		givenProductsWithoutComparablePrice();

		assertThat(comparisonService.goodIdsWithoutPrice(
				List.of(NO_PRICE, RAMEN, HEADQUARTERS_ONLY, SOUP, OTHER_DAY_ONLY)))
				.containsExactly(NO_PRICE, HEADQUARTERS_ONLY, OTHER_DAY_ONLY);
	}

	@Test
	void 어느_지역에도_가격이_있으면_선택_지역과_관계없이_가격이_있는_상품이다() {
		assertThat(comparisonService.goodIdsWithoutPrice(List.of(SOUP))).isEmpty();
	}

	@Test
	void 빈_입력은_DB를_조회하지_않고_빈_집합이다() {
		long count = statementCount(() -> assertThat(comparisonService.goodIdsWithoutPrice(List.of())).isEmpty());

		assertThat(count).isZero();
	}

	@Test
	void 상품이_늘어도_가격_부재_조회는_SQL_한_번이다() {
		givenProductsWithoutComparablePrice();

		long one = statementCount(() -> comparisonService.goodIdsWithoutPrice(List.of(NO_PRICE)));
		long many = statementCount(() -> comparisonService.goodIdsWithoutPrice(
				List.of(NO_PRICE, RAMEN, HEADQUARTERS_ONLY, SOUP, OTHER_DAY_ONLY, RICE_SOUP, RICE)));

		assertThat(one).isEqualTo(1L);
		assertThat(many).isEqualTo(1L);
	}

	private void givenProductsWithoutComparablePrice() {
		em.persist(new Product(NO_PRICE, "가격없는상품", "100", "G"));
		em.persist(new Product(HEADQUARTERS_ONLY, "본사가격만있는상품", "100", "G"));
		em.persist(new Product(OTHER_DAY_ONLY, "다른조사일만있는상품", "100", "G"));
		em.flush();
		prices(em.find(Store.class, 115L), DAY, HEADQUARTERS_ONLY, 777);
		prices(em.find(Store.class, 1L), OTHER_DAY, OTHER_DAY_ONLY, 888);
		em.flush();
		em.clear();
	}

	private long statementCount(Runnable call) {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		em.clear();
		statistics.clear();
		call.run();
		long count = statistics.getPrepareStatementCount();
		System.out.println("[statement-count] " + count);
		return count;
	}

	private void assertInvalid(String areaCode, List<CartLine> cart, InvalidComparisonRequestException.Reason reason) {
		assertThatThrownBy(() -> comparisonService.compare(areaCode, cart))
				.isInstanceOfSatisfying(InvalidComparisonRequestException.class,
						e -> assertThat(e.getReason()).isEqualTo(reason));
	}

	private void region(String code, String name) {
		em.persist(new Region(code, name));
	}

	private Store store(long entpId, String areaCode, String name, String roadAddrBasic, boolean excluded) {
		Store store = new Store(entpId, name, excluded ? "CS" : "SM", areaCode, areaCode.substring(0, 4) + "01000",
				roadAddrBasic, null, "지번 주소 " + entpId, null, excluded, excluded ? "제외 ID 목록" : null);
		em.persist(store);
		return store;
	}

	private void prices(Store store, LocalDate day, Object... goodIdAndPrice) {
		for (int i = 0; i < goodIdAndPrice.length; i += 2) {
			long goodId = ((Number) goodIdAndPrice[i]).longValue();
			int price = (Integer) goodIdAndPrice[i + 1];
			em.persist(new Price(store, em.find(Product.class, goodId), day, price));
		}
	}

	private static List<CartLine> cart(Object... goodIdAndQuantity) {
		List<CartLine> lines = new ArrayList<>();
		for (int i = 0; i < goodIdAndQuantity.length; i += 2) {
			lines.add(new CartLine(((Number) goodIdAndQuantity[i]).longValue(), (Integer) goodIdAndQuantity[i + 1]));
		}
		return lines;
	}
}
