package com.martpick.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.martpick.catalog.PriceRepository;
import com.martpick.catalog.ProductRepository;
import com.martpick.catalog.RegionRepository;
import com.martpick.catalog.Store;
import com.martpick.catalog.StoreRepository;
import com.martpick.config.MartPickProperties;
import com.martpick.publicdata.PriceRow;
import com.martpick.publicdata.ProductRow;
import com.martpick.publicdata.PublicDataApiException;
import com.martpick.publicdata.PublicDataClient;
import com.martpick.publicdata.RegionRow;
import com.martpick.publicdata.StoreRow;

/**
 * 수집기가 직접 트랜잭션을 관리하므로 테스트 트랜잭션을 쓰지 않고(NOT_SUPPORTED),
 * 테스트 전용 DB(martpick_test)의 테이블을 테스트마다 비운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataCollectorTest {

	private static final LocalDate DAY = LocalDate.of(2026, 9, 25);
	private static final LocalDate OTHER_DAY = LocalDate.of(2026, 9, 18);
	private static final String SEOUL = "020100000";
	private static final String GYEONGGI = "020900000";
	private static final long RAMEN = 1000L;
	private static final long SOUP = 1003L;
	/** 실제 수집 대상 7개(1000, 1003, 1008, 292, 254, 224, 993)에 없는 상품 */
	private static final long NOT_TARGET = 553L;

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PriceRepository priceRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	private PublicDataClient client;

	@BeforeEach
	void setUp() {
		cleanTables();
		client = mock(PublicDataClient.class);
	}

	@AfterEach
	void tearDown() {
		cleanTables();
	}

	@Test
	void 첫_수집은_시도_코드와_판매점_대상_상품_가격을_저장한다() {
		givenApi(regions(), List.of(store(1, SEOUL), store(2, GYEONGGI)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280), price(2, RAMEN, 1150)),
						SOUP, List.of(price(1, SOUP, 1980), price(2, SOUP, 1480))));

		CollectionReport report = collector(false).collect();

		assertThat(regionRepository.findAll()).extracting("code").containsExactlyInAnyOrder(SEOUL, GYEONGGI);
		assertThat(storeRepository.count()).isEqualTo(2);
		assertThat(productRepository.findAll()).extracting("goodId").containsExactlyInAnyOrder(RAMEN, SOUP);
		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder("1-1000-1280", "2-1000-1150", "1-1003-1980", "2-1003-1480");
		assertThat(report.totalPriceCount()).isEqualTo(4);
		assertThat(report.priceCounts()).containsEntry(RAMEN, 2).containsEntry(SOUP, 2);
	}

	@Test
	void 본사는_명칭_패턴과_제외_ID로_제외하고_본점은_제외하지_않는다() {
		givenApi(regions(),
				List.of(store(1, SEOUL, "테스트편의점(본사)"), store(115, SEOUL, "세븐일레븐본부"),
						store(106, SEOUL, "신세계백화점본점"), store(2, SEOUL, "테스트마트")),
				products(), Map.of(RAMEN, List.of(price(2, RAMEN, 1280)), SOUP, List.of(price(2, SOUP, 1980))));

		CollectionReport report = collector(false).collect();

		assertThat(excluded(1)).isTrue();
		assertThat(excluded(115)).isTrue();
		assertThat(excluded(106)).isFalse();
		assertThat(excluded(2)).isFalse();
		assertThat(report.excludedStoreCount()).isEqualTo(2);
		assertThat(report.warnings()).anyMatch(w -> w.contains("(본사)") && w.contains("1"));
	}

	@Test
	void 재수집하면_가격을_교체하고_응답에서_빠진_판매점의_옛_가격은_남지_않는다() {
		givenApi(regions(), List.of(store(1, SEOUL), store(2, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280), price(2, RAMEN, 1150)),
						SOUP, List.of(price(1, SOUP, 1980), price(2, SOUP, 1480))));
		collector(false).collect();

		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1300)), SOUP, List.of(price(1, SOUP, 2000))));
		CollectionReport report = collector(true).collect();

		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder("1-1000-1300", "1-1003-2000");
		assertThat(storeRepository.existsById(2L)).as("응답에서 빠진 판매점은 삭제하지 않는다").isTrue();
		assertThat(report.warnings()).anyMatch(w -> w.contains("응답에 없는 기존 판매점") && w.contains("2"));
	}

	@Test
	void 재수집해도_다른_조사일과_수집_대상이_아닌_상품의_가격은_유지된다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		collector(false).collect();
		jdbc.update("insert into product values (?, '신라면(5개입)', '5', 'EA')", NOT_TARGET);
		jdbc.update("insert into price (entp_id, good_id, inspect_day, price) values (1, ?, ?, 900)", RAMEN, OTHER_DAY);
		jdbc.update("insert into price (entp_id, good_id, inspect_day, price) values (1, ?, ?, 2000)", NOT_TARGET, DAY);

		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1300)), SOUP, List.of(price(1, SOUP, 2000))));
		collector(false).collect();

		assertThat(pricesOf(OTHER_DAY)).containsExactly("1-1000-900");
		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder("1-1000-1300", "1-1003-2000", "1-553-2000");
	}

	@Test
	void 가격_API_호출이_하나라도_실패하면_아무것도_저장하지_않는다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(), Map.of(RAMEN, List.of(price(1, RAMEN, 1280))));
		when(client.fetchPrices(DAY, SOUP)).thenThrow(
				new PublicDataApiException(PublicDataApiException.Type.NETWORK, "getProductPriceInfoSvc", "요청 실패"));

		assertThatThrownBy(() -> collector(false).collect()).isInstanceOf(PublicDataApiException.class);

		assertThat(tableCounts()).containsOnly(0L);
	}

	@Test
	void 재수집_중_API가_실패하면_기존_데이터가_유지된다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		collector(false).collect();

		when(client.fetchStores()).thenReturn(List.of(store(1, SEOUL, "이름변경마트")));
		when(client.fetchPrices(DAY, SOUP)).thenThrow(
				new PublicDataApiException(PublicDataApiException.Type.HTTP_ERROR, "getProductPriceInfoSvc", "HTTP 500"));

		assertThatThrownBy(() -> collector(false).collect()).isInstanceOf(PublicDataApiException.class);

		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder("1-1000-1280", "1-1003-1980");
		assertThat(storeRepository.findById(1L).orElseThrow().getName()).isEqualTo("테스트마트");
	}

	@Test
	void 저장_중_DB_오류가_나면_기존_가격_삭제까지_롤백된다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		collector(false).collect();

		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1300)), SOUP, List.of(price(1, SOUP, 0))));

		assertThatThrownBy(() -> collector(false).collect()).isNotInstanceOf(DataCollectionException.class);

		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder("1-1000-1280", "1-1003-1980");
	}

	@Test
	void V1_판매점의_지역코드가_시도_목록에_없으면_실패한다() {
		givenApi(regions(), List.of(store(1, SEOUL), store(2, "021800000")), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V1", "021800000");
	}

	@Test
	void V1_지역코드_응답에_시도_코드가_없으면_실패한다() {
		givenApi(List.of(new RegionRow("020101000", "도봉구", SEOUL)), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V1", "시·도");
	}

	@Test
	void V2_대상_상품이_상품_응답에_없으면_실패한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), List.of(product(RAMEN)),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V2", "1003");
	}

	@Test
	void V3_판매점_ID가_중복되면_실패한다() {
		givenApi(regions(), List.of(store(1, SEOUL), store(1, GYEONGGI)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V3", "1");
	}

	@Test
	void V4_대상_상품의_기준_조사일_가격이_0건이면_실패한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of()));

		assertValidationFailure("V4", "1003");
	}

	@Test
	void V5_가격의_판매점이_판매점_응답에_없으면_실패한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280), price(99, RAMEN, 1150)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V5", "99");
	}

	@Test
	void V6_같은_판매점_상품_조사일의_가격이_중복되면_실패한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280), price(1, RAMEN, 1300)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V6", "1");
	}

	@Test
	void 가격이_기존의_80퍼센트_미만이면_중단하고_기존_가격을_유지한다() {
		givenApi(regions(), stores(5), products(),
				Map.of(RAMEN, prices(5, RAMEN), SOUP, prices(5, SOUP)));
		collector(false).collect();

		givenApi(regions(), stores(5), products(),
				Map.of(RAMEN, prices(3, RAMEN), SOUP, prices(5, SOUP)));

		assertThatThrownBy(() -> collector(false).collect())
				.isInstanceOf(DataCollectionException.class)
				.hasMessageContaining("1000");
		assertThat(pricesOf(DAY)).hasSize(10);
	}

	@Test
	void 허용_옵션이면_급감해도_저장하고_경고를_남긴다() {
		givenApi(regions(), stores(5), products(), Map.of(RAMEN, prices(5, RAMEN), SOUP, prices(5, SOUP)));
		collector(false).collect();

		givenApi(regions(), stores(5), products(), Map.of(RAMEN, prices(3, RAMEN), SOUP, prices(5, SOUP)));
		CollectionReport report = collector(true).collect();

		assertThat(pricesOf(DAY)).hasSize(8);
		assertThat(report.warnings()).anyMatch(w -> w.contains("1000") && w.contains("허용"));
	}

	@Test
	void 허용_옵션이어도_필수_검증은_생략하지_않는다() {
		givenApi(regions(), stores(5), products(), Map.of(RAMEN, prices(5, RAMEN), SOUP, prices(5, SOUP)));
		collector(false).collect();

		List<PriceRow> withUnknownStore = new ArrayList<>(prices(3, RAMEN));
		withUnknownStore.add(price(99, RAMEN, 1000));
		givenApi(regions(), stores(5), products(), Map.of(RAMEN, withUnknownStore, SOUP, prices(5, SOUP)));

		assertThatThrownBy(() -> collector(true).collect())
				.isInstanceOf(DataCollectionException.class)
				.hasMessageContaining("V5");
		assertThat(pricesOf(DAY)).hasSize(10);
	}

	@Test
	void 일시적_오류_후_성공하면_재시도해서_저장하고_보고에_기록한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		when(client.fetchPrices(DAY, SOUP)).thenThrow(networkError()).thenReturn(List.of(price(1, SOUP, 1980)));

		CollectionReport report = collector(false).collect();

		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder("1-1000-1280", "1-1003-1980");
		assertThat(report.retries()).containsExactly(Map.entry("가격 goodId=1003", 1));
		assertThat(report.retryCount()).isEqualTo(1);
	}

	@Test
	void HTTP_5xx가_두_번_나도_세_번째_시도에_성공하면_저장한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		when(client.fetchPrices(DAY, SOUP)).thenThrow(httpError(503), httpError(500))
				.thenReturn(List.of(price(1, SOUP, 1980)));

		CollectionReport report = collector(false).collect();

		verify(client, times(3)).fetchPrices(DAY, SOUP);
		assertThat(pricesOf(DAY)).hasSize(2);
		assertThat(report.retries()).containsEntry("가격 goodId=1003", 2);
	}

	@Test
	void 세_번_모두_일시적_오류면_중단하고_아무것도_저장하지_않는다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		when(client.fetchPrices(DAY, SOUP)).thenThrow(networkError());

		assertThatThrownBy(() -> collector(false).collect()).isInstanceOf(PublicDataApiException.class);

		verify(client, times(3)).fetchPrices(DAY, SOUP);
		assertThat(tableCounts()).containsOnly(0L);
	}

	@Test
	void 재시도_대상이_아닌_오류는_한_번만_호출하고_중단한다() {
		List<PublicDataApiException> failures = List.of(httpError(401), httpError(429), httpError(400),
				new PublicDataApiException(PublicDataApiException.Type.API_ERROR, "getProductPriceInfoSvc", "resultCode=30"),
				new PublicDataApiException(PublicDataApiException.Type.INVALID_ROW, "getProductPriceInfoSvc", "형식 오류"));
		for (PublicDataApiException failure : failures) {
			client = mock(PublicDataClient.class);
			givenApi(regions(), List.of(store(1, SEOUL)), products(),
					Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
			when(client.fetchPrices(DAY, SOUP)).thenThrow(failure);

			assertThatThrownBy(() -> collector(false).collect()).as(failure.getMessage()).isSameAs(failure);

			verify(client, times(1)).fetchPrices(DAY, SOUP);
			assertThat(tableCounts()).containsOnly(0L);
		}
	}

	@Test
	void 가격_외의_API_호출도_일시적_오류면_재시도한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		when(client.fetchStores()).thenThrow(networkError()).thenReturn(List.of(store(1, SEOUL)));

		CollectionReport report = collector(false).collect();

		assertThat(storeRepository.count()).isEqualTo(1);
		assertThat(report.retries()).containsExactly(Map.entry("판매점", 1));
	}

	@Test
	void 수집_1회의_재시도_총합이_상한에_도달하면_더_재시도하지_않고_중단한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		when(client.fetchStores()).thenThrow(networkError()).thenReturn(List.of(store(1, SEOUL)));
		when(client.fetchPrices(DAY, RAMEN)).thenThrow(networkError()).thenReturn(List.of(price(1, RAMEN, 1280)));
		when(client.fetchPrices(DAY, SOUP)).thenThrow(networkError()).thenReturn(List.of(price(1, SOUP, 1980)));

		assertThatThrownBy(() -> collector(false, 2).collect())
				.isInstanceOf(DataCollectionException.class)
				.hasMessageContaining("재시도")
				.hasMessageContaining("2회")
				.hasMessageContaining("goodId=1003");

		verify(client, times(1)).fetchPrices(DAY, SOUP);
		assertThat(tableCounts()).containsOnly(0L);
	}

	@Test
	void 재시도_총합이_상한과_같으면_정상_저장한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		when(client.fetchStores()).thenThrow(networkError()).thenReturn(List.of(store(1, SEOUL)));
		when(client.fetchPrices(DAY, SOUP)).thenThrow(networkError()).thenReturn(List.of(price(1, SOUP, 1980)));

		CollectionReport report = collector(false, 2).collect();

		assertThat(report.retryCount()).isEqualTo(2);
		assertThat(pricesOf(DAY)).hasSize(2);
	}

	@Test
	void 응답_건수가_딱_떨어지면_경고만_남기고_저장한다() {
		givenApi(regions(), stores(100), products(), Map.of(RAMEN, prices(100, RAMEN), SOUP, prices(3, SOUP)));

		CollectionReport report = collector(false).collect();

		assertThat(storeRepository.count()).isEqualTo(100);
		assertThat(report.warnings()).anyMatch(w -> w.contains("참고 신호") && w.contains("100"));
	}

	private DataCollector collector(boolean allowShrink) {
		return collector(allowShrink, 30);
	}

	/**
	 * 재시도 대기 시간은 테스트 속도를 위해 0으로 둔다. 운영 값(1초·2초)은 설정 바인딩 테스트로 확인한다.
	 */
	private DataCollector collector(boolean allowShrink, int maxTotalRetries) {
		MartPickProperties properties = new MartPickProperties(DAY,
				new MartPickProperties.Collect(false, List.of(RAMEN, SOUP), List.of(115L), allowShrink,
						List.of(Duration.ZERO, Duration.ZERO), maxTotalRetries));
		return new DataCollector(client, properties, regionRepository, storeRepository, productRepository,
				priceRepository, new TransactionTemplate(transactionManager));
	}

	private static PublicDataApiException networkError() {
		return new PublicDataApiException(PublicDataApiException.Type.NETWORK, "getProductPriceInfoSvc",
				"요청 실패 (SocketTimeoutException)");
	}

	private static PublicDataApiException httpError(int status) {
		return new PublicDataApiException(PublicDataApiException.Type.HTTP_ERROR, "getProductPriceInfoSvc",
				"HTTP " + status, status);
	}

	private void givenApi(List<RegionRow> regions, List<StoreRow> stores, List<ProductRow> products,
			Map<Long, List<PriceRow>> prices) {
		when(client.fetchRegions()).thenReturn(regions);
		when(client.fetchStores()).thenReturn(stores);
		when(client.fetchProducts()).thenReturn(products);
		when(client.fetchPrices(eq(DAY), any(Long.class))).thenReturn(List.of());
		prices.forEach((goodId, rows) -> when(client.fetchPrices(DAY, goodId)).thenReturn(rows));
	}

	private void assertValidationFailure(String rule, String detail) {
		assertThatThrownBy(() -> collector(false).collect())
				.isInstanceOf(DataCollectionException.class)
				.hasMessageContaining(rule)
				.hasMessageContaining(detail);
		assertThat(tableCounts()).containsOnly(0L);
	}

	private static List<RegionRow> regions() {
		return List.of(
				new RegionRow(SEOUL, "서울특별시", "020000000"),
				new RegionRow("020101000", "도봉구", SEOUL),
				new RegionRow(GYEONGGI, "경기도", "020000000"));
	}

	private static List<ProductRow> products() {
		return List.of(product(RAMEN), product(SOUP), product(NOT_TARGET));
	}

	private static ProductRow product(long goodId) {
		return new ProductRow(goodId, "상품" + goodId, "100", "G");
	}

	private static StoreRow store(long entpId, String areaCode) {
		return store(entpId, areaCode, "테스트마트");
	}

	private static StoreRow store(long entpId, String areaCode, String name) {
		return new StoreRow(entpId, name, "SM", areaCode, areaCode.substring(0, 4) + "01000",
				null, null, "테스트 주소 " + entpId, null);
	}

	private static List<StoreRow> stores(int count) {
		List<StoreRow> stores = new ArrayList<>();
		for (long id = 1; id <= count; id++) {
			stores.add(store(id, SEOUL));
		}
		return stores;
	}

	private static PriceRow price(long entpId, long goodId, int price) {
		return new PriceRow(DAY, entpId, goodId, price);
	}

	private static List<PriceRow> prices(int storeCount, long goodId) {
		List<PriceRow> prices = new ArrayList<>();
		for (long id = 1; id <= storeCount; id++) {
			prices.add(price(id, goodId, 1000));
		}
		return prices;
	}

	private boolean excluded(long entpId) {
		Store store = storeRepository.findById(entpId).orElseThrow();
		return store.isExcluded();
	}

	private List<String> pricesOf(LocalDate day) {
		return jdbc.queryForList(
				"select entp_id || '-' || good_id || '-' || price from price where inspect_day = ?", String.class, day);
	}

	private List<Long> tableCounts() {
		return List.of(
				jdbc.queryForObject("select count(*) from region", Long.class),
				jdbc.queryForObject("select count(*) from store", Long.class),
				jdbc.queryForObject("select count(*) from product", Long.class),
				jdbc.queryForObject("select count(*) from price", Long.class));
	}

	private void cleanTables() {
		jdbc.update("delete from cart_item");
		jdbc.update("delete from member");
		jdbc.update("delete from price");
		jdbc.update("delete from store");
		jdbc.update("delete from product");
		jdbc.update("delete from region");
	}
}
