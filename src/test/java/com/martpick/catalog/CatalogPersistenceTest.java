package com.martpick.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 로컬 PostgreSQL(compose.yaml)에 Flyway 스키마를 적용한 상태로 실행한다.
 * 각 테스트는 롤백되며, 수집된 실제 데이터와 겹치지 않도록 테스트 전용 ID를 사용한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CatalogPersistenceTest {

	private static final String TEST_AREA_CODE = "029900000";
	private static final long TEST_GOOD_ID = 9_000_001L;
	private static final long TEN_DIGIT_ENTP_ID = 9_999_999_001L;
	private static final LocalDate INSPECT_DAY = LocalDate.of(2026, 9, 25);

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private PriceRepository priceRepository;

	@Autowired
	private TestEntityManager em;

	private Product product;

	@BeforeEach
	void setUp() {
		regionRepository.save(new Region(TEST_AREA_CODE, "테스트시"));
		product = productRepository.save(new Product(TEST_GOOD_ID, "테스트 컵라면(110g)", "110", "G"));
	}

	@Test
	void 열자리_판매점_ID를_저장하고_조회한다() {
		storeRepository.saveAndFlush(store(TEN_DIGIT_ENTP_ID, TEST_AREA_CODE));
		em.clear();

		Store found = storeRepository.findById(TEN_DIGIT_ENTP_ID).orElseThrow();

		assertThat(found.getEntpId()).isEqualTo(TEN_DIGIT_ENTP_ID);
		assertThat(found.getAreaCode()).isEqualTo(TEST_AREA_CODE);
		assertThat(found.getRoadAddrBasic()).isNull();
		assertThat(found.isExcluded()).isFalse();
	}

	@Test
	void 본사_제외_여부와_사유를_저장한다() {
		storeRepository.saveAndFlush(new Store(TEN_DIGIT_ENTP_ID, "테스트편의점(본사)", "CS", TEST_AREA_CODE,
				"029901000", null, null, "테스트시 테스트구 1", null, true, "판매점명에 (본사) 포함"));
		em.clear();

		Store found = storeRepository.findById(TEN_DIGIT_ENTP_ID).orElseThrow();

		assertThat(found.isExcluded()).isTrue();
		assertThat(found.getExcludedReason()).isEqualTo("판매점명에 (본사) 포함");
	}

	@Test
	void region에_없는_지역코드의_판매점은_저장할_수_없다() {
		assertThatThrownBy(() -> storeRepository.saveAndFlush(store(TEN_DIGIT_ENTP_ID, "029999999")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 가격을_판매점_상품_조사일과_함께_저장하고_조회한다() {
		Store store = storeRepository.save(store(TEN_DIGIT_ENTP_ID, TEST_AREA_CODE));
		Price saved = priceRepository.saveAndFlush(new Price(store, product, INSPECT_DAY, 1280));
		em.clear();

		Price found = priceRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getStore().getEntpId()).isEqualTo(TEN_DIGIT_ENTP_ID);
		assertThat(found.getProduct().getGoodId()).isEqualTo(TEST_GOOD_ID);
		assertThat(found.getInspectDay()).isEqualTo(INSPECT_DAY);
		assertThat(found.getPrice()).isEqualTo(1280);
	}

	@Test
	void 같은_판매점_상품_조사일의_가격은_중복_저장할_수_없다() {
		Store store = storeRepository.save(store(TEN_DIGIT_ENTP_ID, TEST_AREA_CODE));
		priceRepository.saveAndFlush(new Price(store, product, INSPECT_DAY, 1280));

		assertThatThrownBy(() -> priceRepository.saveAndFlush(new Price(store, product, INSPECT_DAY, 1300)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 조사일이_다르면_같은_판매점_상품의_가격을_각각_저장한다() {
		Store store = storeRepository.save(store(TEN_DIGIT_ENTP_ID, TEST_AREA_CODE));
		priceRepository.saveAndFlush(new Price(store, product, INSPECT_DAY, 1280));
		priceRepository.saveAndFlush(new Price(store, product, INSPECT_DAY.minusDays(7), 1300));

		assertThat(priceRepository.findAll())
				.filteredOn(p -> p.getStore().getEntpId() == TEN_DIGIT_ENTP_ID)
				.hasSize(2);
	}

	@Test
	void 가격이_0원이면_저장할_수_없다() {
		Store store = storeRepository.save(store(TEN_DIGIT_ENTP_ID, TEST_AREA_CODE));

		assertThatThrownBy(() -> priceRepository.saveAndFlush(new Price(store, product, INSPECT_DAY, 0)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Store store(long entpId, String areaCode) {
		return new Store(entpId, "테스트마트", "SM", areaCode, "029901000",
				null, null, "테스트시 테스트구 1", null, false, null);
	}
}
