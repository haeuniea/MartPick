package com.martpick.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
	/** 테스트의 기준 상품은 RAMEN, SOUP 두 개로 둔다. */
	private static final long RAMEN = 1000L;
	private static final long SOUP = 1003L;
	/** 기준 상품이 아닌 상품 (실제 기준 상품 7개에도 없음) */
	private static final long EXTRA = 553L;

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PriceRepository priceRepository;

	@Autowired
	private CollectionRunRepository collectionRunRepository;

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
	void 첫_수집은_시도_코드와_판매점과_상품_응답의_전체_상품_가격을_저장한다() {
		givenApi(regions(), List.of(store(1, SEOUL), store(2, GYEONGGI)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280), price(2, RAMEN, 1150)),
						SOUP, List.of(price(1, SOUP, 1980), price(2, SOUP, 1480)),
						EXTRA, List.of(price(2, EXTRA, 4500))));

		CollectionReport report = collector(false).collect();

		assertThat(regionRepository.findAll()).extracting("code").containsExactlyInAnyOrder(SEOUL, GYEONGGI);
		assertThat(storeRepository.count()).isEqualTo(2);
		assertThat(productRepository.findAll()).extracting("goodId").containsExactlyInAnyOrder(RAMEN, SOUP, EXTRA);
		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder(
				"1-1000-1280", "2-1000-1150", "1-1003-1980", "2-1003-1480", "2-553-4500");
		assertThat(report.totalPriceCount()).isEqualTo(5);
		assertThat(report.baselinePriceCounts()).containsExactly(Map.entry(RAMEN, 2), Map.entry(SOUP, 2));
	}

	@Test
	void 가격_조회_대상은_기준_상품이_아니라_상품_응답의_전체_상품이다() {
		long onlyInDb = 8888L;
		jdbc.update("insert into product values (?, 'DB에만 있는 상품', '1', 'EA')", onlyInDb);
		givenApi(regions(), List.of(store(1, SEOUL)),
				List.of(product(RAMEN), product(SOUP), product(EXTRA), product(7777L)),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		collector(false).collect();

		for (long goodId : new long[] {RAMEN, SOUP, EXTRA, 7777L}) {
			verify(client, times(1)).fetchPrices(DAY, goodId);
		}
		verify(client, never()).fetchPrices(DAY, onlyInDb);
	}

	@Test
	void 기준_외_상품은_기준_조사일_가격이_0건이어도_저장한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980)), EXTRA, List.of()));

		CollectionReport report = collector(false).collect();

		assertThat(productRepository.existsById(EXTRA)).isTrue();
		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder("1-1000-1280", "1-1003-1980");
		assertThat(report.productCount()).isEqualTo(3);
		assertThat(report.pricedProductCount()).isEqualTo(2);
		assertThat(report.noPriceProductCount()).isEqualTo(1);
	}

	@Test
	void 상품_응답_건수와_가격을_조회한_상품_수_가격_건수_분포를_보고한다() {
		List<ProductRow> products = List.of(product(RAMEN), product(SOUP), product(EXTRA), product(7777L));
		givenApi(regions(), stores(150), products,
				Map.of(RAMEN, prices(150, RAMEN), SOUP, prices(5, SOUP), EXTRA, prices(100, EXTRA)));

		CollectionReport report = collector(false).collect();

		assertThat(report.productCount()).isEqualTo(4);
		assertThat(report.priceQueriedProductCount()).isEqualTo(4);
		assertThat(report.pricedProductCount()).isEqualTo(3);
		assertThat(report.noPriceProductCount()).isEqualTo(1);
		assertThat(report.totalPriceCount()).isEqualTo(255);
		assertThat(report.priceCountDistribution())
				.containsExactly(Map.entry("0", 1), Map.entry("1-99", 1), Map.entry("100-199", 2));
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
	void 재수집해도_다른_조사일의_가격은_유지된다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));
		collector(false).collect();
		jdbc.update("insert into price (entp_id, good_id, inspect_day, price) values (1, ?, ?, 900)", RAMEN, OTHER_DAY);

		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1300)), SOUP, List.of(price(1, SOUP, 2000))));
		collector(false).collect();

		assertThat(pricesOf(OTHER_DAY)).containsExactly("1-1000-900");
		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder("1-1000-1300", "1-1003-2000");
	}

	@Test
	void 상품_응답에서_사라진_상품은_상품을_유지하고_조사일_가격은_전체_교체로_남지_않는다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980)),
						EXTRA, List.of(price(1, EXTRA, 4500))));
		collector(false).collect();
		jdbc.update("insert into price (entp_id, good_id, inspect_day, price) values (1, ?, ?, 4400)", EXTRA, OTHER_DAY);
		clearInvocations(client);

		givenApi(regions(), List.of(store(1, SEOUL)), List.of(product(RAMEN), product(SOUP)),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1300)), SOUP, List.of(price(1, SOUP, 2000))));
		// 3개 중 1개 상품이 사라져 G2에 걸리므로 허용 옵션으로 저장 단계까지 진행시킨다.
		CollectionReport report = collector(true).collect();

		assertThat(productRepository.existsById(EXTRA)).as("사라진 상품의 상품 레코드는 유지").isTrue();
		assertThat(pricesOf(DAY)).containsExactlyInAnyOrder("1-1000-1300", "1-1003-2000");
		assertThat(pricesOf(OTHER_DAY)).containsExactly("1-553-4400");
		verify(client, never()).fetchPrices(DAY, EXTRA);
		assertThat(report.missingProducts()).containsExactly(Map.entry(EXTRA, 1L));
		assertThat(report.warnings()).anyMatch(w -> w.contains("응답에 없는 기존 상품") && w.contains("553"));
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
	void 저장_중_DB_오류가_나면_조사일_전체_가격_삭제까지_롤백된다() {
		givenApi(regions(), List.of(store(1, SEOUL)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980)),
						EXTRA, List.of(price(1, EXTRA, 4500))));
		collector(false).collect();

		givenApi(regions(), List.of(store(1, SEOUL, "이름변경마트")), List.of(product(RAMEN), product(SOUP)),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1300)), SOUP, List.of(price(1, SOUP, 0))));

		// 상품이 줄어 G2에 걸리지 않도록 허용 옵션으로 저장 단계까지 진행시킨다.
		assertThatThrownBy(() -> collector(true).collect()).isNotInstanceOf(DataCollectionException.class);

		assertThat(pricesOf(DAY)).as("사라진 상품의 가격까지 삭제 전 상태로 롤백")
				.containsExactlyInAnyOrder("1-1000-1280", "1-1003-1980", "1-553-4500");
		assertThat(storeRepository.findById(1L).orElseThrow().getName()).isEqualTo("테스트마트");
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
	void V2_기준_상품이_상품_응답에_없으면_실패한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), List.of(product(RAMEN), product(EXTRA)),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V2", "1003");
	}

	@Test
	void V2_상품_응답의_상품_ID가_중복되면_실패한다() {
		givenApi(regions(), List.of(store(1, SEOUL)), List.of(product(RAMEN), product(SOUP), product(EXTRA), product(EXTRA)),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V2", "553");
	}

	@Test
	void 지역_판매점_상품_응답_검증에_실패하면_가격_API를_호출하지_않는다() {
		givenApi(regions(), List.of(store(1, SEOUL), store(1, GYEONGGI)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V3", "1");
		verify(client, never()).fetchPrices(any(), anyLong());
	}

	@Test
	void V3_판매점_ID가_중복되면_실패한다() {
		givenApi(regions(), List.of(store(1, SEOUL), store(1, GYEONGGI)), products(),
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		assertValidationFailure("V3", "1");
	}

	@Test
	void V4_기준_상품의_기준_조사일_가격이_0건이면_실패한다() {
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
	void 기준_외_상품의_가격이_줄어도_상품별_급감_안전장치에_걸리지_않는다() {
		// 전체 건수는 80% 이상(45 → 41)으로 유지해 G2와 무관하게 G1만 확인한다.
		givenApi(regions(), stores(20), products(),
				Map.of(RAMEN, prices(20, RAMEN), SOUP, prices(20, SOUP), EXTRA, prices(5, EXTRA)));
		collector(false).collect();

		givenApi(regions(), stores(20), products(),
				Map.of(RAMEN, prices(20, RAMEN), SOUP, prices(20, SOUP), EXTRA, prices(1, EXTRA)));
		collector(false).collect();

		assertThat(pricesOf(DAY)).hasSize(41);
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
	void 첫_전체_수집은_G2를_생략하고_첫_성공_이력을_기록한다() {
		givenG2Base();

		CollectionReport report = collector(false).collect();

		assertThat(report.g2Applied()).isFalse();
		assertThat(report.previousRunId()).isNull();
		assertThat(report.warnings()).anyMatch(w -> w.contains("이전 전체 수집 이력 없음") && w.contains("G2"));
		assertThat(runs()).containsExactly(DAY + "|10|10|100");
		assertThat(report.runId()).isEqualTo(latestRunId());
	}

	@Test
	void 두_번째_전체_수집은_이전_성공_이력으로_G2를_적용하고_새_이력을_기록한다() {
		givenG2Base();
		long firstRunId = collector(false).collect().runId();

		givenG2Base();
		CollectionReport report = collector(false).collect();

		assertThat(report.g2Applied()).isTrue();
		assertThat(report.previousRunId()).isEqualTo(firstRunId);
		assertThat(report.previousPriceCount()).isEqualTo(100);
		assertThat(report.previousPricedProductCount()).isEqualTo(10);
		assertThat(report.totalPriceCount()).isEqualTo(100);
		assertThat(report.pricedProductCount()).isEqualTo(10);
		assertThat(report.runId()).isNotEqualTo(firstRunId).isEqualTo(latestRunId());
		assertThat(runs()).hasSize(2);
	}

	@Test
	void G2_전체_가격_건수가_이전_이력의_80퍼센트_미만이면_중단하고_기존_가격과_이력을_유지한다() {
		givenG2Base();
		collector(false).collect();

		givenG2Api(10, 10, 10, 10, 10, 10, 10, 5, 4, 0);

		assertThatThrownBy(() -> collector(false).collect())
				.isInstanceOf(DataCollectionException.class)
				.hasMessageContaining("G2")
				.hasMessageContaining("전체 가격 건수")
				.hasMessageContaining("100")
				.hasMessageContaining("79");
		assertThat(pricesOf(DAY)).hasSize(100);
		assertThat(runs()).hasSize(1);
	}

	@Test
	void G2_가격_보유_상품_수가_이전_이력의_80퍼센트_미만이면_중단한다() {
		givenG2Base();
		collector(false).collect();

		givenG2Api(10, 10, 12, 12, 12, 12, 12, 0, 0, 0);

		assertThatThrownBy(() -> collector(false).collect())
				.isInstanceOf(DataCollectionException.class)
				.hasMessageContaining("G2")
				.hasMessageContaining("가격 보유 상품 수")
				.hasMessageContaining("10")
				.hasMessageContaining("7");
		assertThat(pricesOf(DAY)).hasSize(100);
		assertThat(runs()).hasSize(1);
	}

	@Test
	void G2_전체_가격_건수와_가격_보유_상품_수가_정확히_80퍼센트면_통과한다() {
		givenG2Base();
		collector(false).collect();

		givenG2Api(10, 10, 10, 10, 10, 10, 10, 10, 0, 0);
		CollectionReport report = collector(false).collect();

		assertThat(report.totalPriceCount()).isEqualTo(80);
		assertThat(report.pricedProductCount()).isEqualTo(8);
		assertThat(pricesOf(DAY)).hasSize(80);
		assertThat(runs()).hasSize(2);
	}

	@Test
	void 허용_옵션은_G2를_경고로_낮춰_저장한다() {
		givenG2Base();
		collector(false).collect();

		givenG2Api(10, 10, 10, 10, 10, 10, 10, 5, 4, 0);
		CollectionReport report = collector(true).collect();

		assertThat(pricesOf(DAY)).hasSize(79);
		assertThat(runs()).hasSize(2);
		assertThat(report.warnings()).anyMatch(w -> w.contains("G2") && w.contains("허용"));
	}

	@Test
	void 이력과_DB_건수가_다르면_API를_호출하지_않고_중단하며_허용_옵션으로도_우회할_수_없다() {
		givenG2Base();
		collector(false).collect();
		jdbc.update("delete from price where id = (select min(id) from price where inspect_day = ?)", DAY);
		clearInvocations(client);

		assertThatThrownBy(() -> collector(true).collect())
				.isInstanceOf(DataCollectionException.class)
				.hasMessageContaining("불일치")
				.hasMessageContaining("100")
				.hasMessageContaining("99");

		verifyNoInteractions(client);
		assertThat(pricesOf(DAY)).hasSize(99);
		assertThat(runs()).hasSize(1);
	}

	@Test
	void API_조회_중_DB가_바뀌면_트랜잭션_안에서_다시_확인해_중단한다() {
		givenG2Base();
		collector(false).collect();
		givenG2Base();
		when(client.fetchProducts()).thenAnswer(invocation -> {
			jdbc.update("delete from price where id = (select min(id) from price where inspect_day = ?)", DAY);
			return g2Products();
		});

		assertThatThrownBy(() -> collector(true).collect())
				.isInstanceOf(DataCollectionException.class)
				.hasMessageContaining("불일치");

		assertThat(pricesOf(DAY)).as("가격 교체 없음").hasSize(99);
		assertThat(runs()).hasSize(1);
	}

	@Test
	void 저장_후_건수가_응답과_다르면_가격_교체와_이력_저장을_롤백하며_허용_옵션으로도_우회할_수_없다() {
		givenG2Base();
		collector(false).collect();
		givenG2ApiWithPrice(2000, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
		PriceRepository lyingAfterDelete = countLyingAfterDelete();

		assertThatThrownBy(() -> collector(true, 30, lyingAfterDelete, collectionRunRepository).collect())
				.isInstanceOf(DataCollectionException.class)
				.hasMessageContaining("저장 후");

		assertThat(pricesOf(DAY)).hasSize(100).allMatch(p -> p.endsWith("-1000"));
		assertThat(runs()).hasSize(1);
	}

	@Test
	void 이력_저장이_실패하면_가격_교체까지_롤백되고_기존_이력이_유지된다() {
		givenG2Base();
		collector(false).collect();
		givenG2ApiWithPrice(2000, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
		CollectionRunRepository failingRuns = mock(CollectionRunRepository.class, delegatesTo(collectionRunRepository));
		doThrow(new IllegalStateException("이력 저장 실패")).when(failingRuns).saveAndFlush(any());

		assertThatThrownBy(() -> collector(false, 30, priceRepository, failingRuns).collect())
				.hasMessageContaining("이력 저장 실패");

		assertThat(pricesOf(DAY)).hasSize(100).allMatch(p -> p.endsWith("-1000"));
		assertThat(runs()).hasSize(1);
	}

	@Test
	void 첫_수집에서_이력_저장이_실패하면_아무것도_저장하지_않는다() {
		givenG2Base();
		CollectionRunRepository failingRuns = mock(CollectionRunRepository.class, delegatesTo(collectionRunRepository));
		doThrow(new IllegalStateException("이력 저장 실패")).when(failingRuns).saveAndFlush(any());

		assertThatThrownBy(() -> collector(false, 30, priceRepository, failingRuns).collect())
				.hasMessageContaining("이력 저장 실패");

		assertThat(tableCounts()).containsOnly(0L);
		assertThat(runs()).isEmpty();
	}

	@Test
	void 응답_건수가_딱_떨어지면_경고만_남기고_저장한다() {
		givenApi(regions(), stores(100), products(), Map.of(RAMEN, prices(100, RAMEN), SOUP, prices(3, SOUP)));

		CollectionReport report = collector(false).collect();

		assertThat(storeRepository.count()).isEqualTo(100);
		assertThat(report.warnings()).anyMatch(w -> w.contains("참고 신호") && w.contains("판매점") && w.contains("100"));
	}

	@Test
	void 상품_응답_건수가_딱_떨어지면_참고_신호를_남긴다() {
		List<ProductRow> products = new ArrayList<>(List.of(product(RAMEN), product(SOUP)));
		for (long id = 1; id <= 98; id++) {
			products.add(product(10_000 + id));
		}
		givenApi(regions(), List.of(store(1, SEOUL)), products,
				Map.of(RAMEN, List.of(price(1, RAMEN, 1280)), SOUP, List.of(price(1, SOUP, 1980))));

		CollectionReport report = collector(false).collect();

		assertThat(report.warnings()).anyMatch(w -> w.contains("참고 신호") && w.contains("상품") && w.contains("100"));
	}

	@Test
	void 상품별_가격_건수가_딱_떨어져도_참고_신호를_남기지_않는다() {
		givenApi(regions(), stores(101), products(), Map.of(RAMEN, prices(100, RAMEN), SOUP, prices(3, SOUP)));

		CollectionReport report = collector(false).collect();

		assertThat(report.warnings()).noneMatch(w -> w.contains("참고 신호"));
	}

	private DataCollector collector(boolean allowShrink) {
		return collector(allowShrink, 30);
	}

	/**
	 * 재시도 대기 시간은 테스트 속도를 위해 0으로 둔다. 운영 값(1초·2초)은 설정 바인딩 테스트로 확인한다.
	 */
	private DataCollector collector(boolean allowShrink, int maxTotalRetries) {
		return collector(allowShrink, maxTotalRetries, priceRepository, collectionRunRepository);
	}

	private DataCollector collector(boolean allowShrink, int maxTotalRetries, PriceRepository prices,
			CollectionRunRepository runs) {
		MartPickProperties properties = new MartPickProperties(DAY,
				new MartPickProperties.Collect(false, List.of(RAMEN, SOUP), List.of(115L), allowShrink,
						List.of(Duration.ZERO, Duration.ZERO), maxTotalRetries));
		return new DataCollector(client, properties, regionRepository, storeRepository, productRepository,
				prices, runs, new TransactionTemplate(transactionManager));
	}

	/** G2 기준 수집: 판매점 12곳, 상품 10개(기준 2 + 기준 외 8)가 각 10건 → 전체 100건, 가격 보유 상품 10개 */
	private void givenG2Base() {
		givenG2Api(10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
	}

	/**
	 * @param counts 가격 건수. 순서대로 RAMEN, SOUP, 기준 외 상품 8개
	 */
	private void givenG2Api(int... counts) {
		givenG2ApiWithPrice(1000, counts);
	}

	private void givenG2ApiWithPrice(int price, int... counts) {
		List<ProductRow> products = g2Products();
		Map<Long, List<PriceRow>> prices = new LinkedHashMap<>();
		for (int i = 0; i < products.size(); i++) {
			long goodId = products.get(i).goodId();
			List<PriceRow> rows = new ArrayList<>();
			for (long entpId = 1; entpId <= counts[i]; entpId++) {
				rows.add(price(entpId, goodId, price));
			}
			prices.put(goodId, rows);
		}
		givenApi(regions(), stores(12), products, prices);
	}

	private static List<ProductRow> g2Products() {
		List<ProductRow> products = new ArrayList<>(List.of(product(RAMEN), product(SOUP)));
		for (long i = 1; i <= 8; i++) {
			products.add(product(20_000 + i));
		}
		return products;
	}

	/**
	 * 조사일 가격 삭제가 실행된 뒤부터 가격 건수를 실제보다 1 크게 돌려주는 리포지토리.
	 * 저장 후 건수 검증만 실패시키고, 수집 전·트랜잭션 시작 시 이력 확인은 실제 값으로 통과시킨다.
	 */
	private PriceRepository countLyingAfterDelete() {
		AtomicBoolean deleted = new AtomicBoolean();
		PriceRepository repository = mock(PriceRepository.class, delegatesTo(priceRepository));
		doAnswer(invocation -> {
			deleted.set(true);
			return priceRepository.deleteByInspectDay(invocation.getArgument(0));
		}).when(repository).deleteByInspectDay(any());
		doAnswer(invocation -> priceRepository.countByInspectDay(invocation.getArgument(0)) + (deleted.get() ? 1 : 0))
				.when(repository).countByInspectDay(any());
		return repository;
	}

	/** 성공 이력을 "조사일|상품 수|가격 보유 상품 수|전체 가격 건수"로 저장 순서대로 반환한다. */
	private List<String> runs() {
		return jdbc.queryForList("select inspect_day || '|' || product_count || '|' || priced_product_count || '|' || price_count"
				+ " from collection_run order by id", String.class);
	}

	private Long latestRunId() {
		return jdbc.queryForObject("select max(id) from collection_run", Long.class);
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
		return List.of(product(RAMEN), product(SOUP), product(EXTRA));
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
		jdbc.update("delete from collection_run");
		jdbc.update("delete from cart_item");
		jdbc.update("delete from member");
		jdbc.update("delete from price");
		jdbc.update("delete from store");
		jdbc.update("delete from product");
		jdbc.update("delete from region");
	}
}
