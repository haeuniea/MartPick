package com.martpick.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 테스트 전용 DB(martpick_test)에 Flyway V2까지 적용한 상태로 실행하며 각 테스트는 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CollectionRunPersistenceTest {

	private static final LocalDate DAY = LocalDate.of(2026, 9, 25);
	private static final LocalDate OTHER_DAY = LocalDate.of(2026, 9, 18);
	/** PostgreSQL timestamptz는 마이크로초까지 저장한다. */
	private static final Instant T0 = Instant.parse("2026-09-30T10:00:00.123456Z");

	@Autowired
	private CollectionRunRepository collectionRunRepository;

	@Autowired
	private TestEntityManager em;

	@Test
	void 성공_이력을_저장하고_모든_값을_그대로_조회한다() {
		CollectionRun saved = collectionRunRepository.saveAndFlush(new CollectionRun(DAY, 604, 450, 120_000, T0));
		em.clear();

		CollectionRun found = collectionRunRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getInspectDay()).isEqualTo(DAY);
		assertThat(found.getProductCount()).isEqualTo(604);
		assertThat(found.getPricedProductCount()).isEqualTo(450);
		assertThat(found.getPriceCount()).isEqualTo(120_000);
		assertThat(found.getCompletedAt()).isEqualTo(T0);
	}

	@Test
	void 같은_조사일의_최신_이력을_완료_시각_기준으로_조회한다() {
		collectionRunRepository.save(new CollectionRun(DAY, 604, 450, 120_000, T0.plus(1, ChronoUnit.HOURS)));
		collectionRunRepository.save(new CollectionRun(DAY, 604, 460, 130_000, T0.plus(2, ChronoUnit.HOURS)));
		collectionRunRepository.save(new CollectionRun(DAY, 604, 440, 110_000, T0));
		collectionRunRepository.save(new CollectionRun(OTHER_DAY, 604, 500, 150_000, T0.plus(3, ChronoUnit.HOURS)));
		collectionRunRepository.flush();
		em.clear();

		CollectionRun latest = collectionRunRepository.findFirstByInspectDayOrderByCompletedAtDescIdDesc(DAY).orElseThrow();

		assertThat(latest.getPriceCount()).isEqualTo(130_000);
		assertThat(latest.getPricedProductCount()).isEqualTo(460);
	}

	@Test
	void 완료_시각이_같으면_나중에_저장한_이력을_최신으로_본다() {
		collectionRunRepository.saveAndFlush(new CollectionRun(DAY, 604, 450, 120_000, T0));
		CollectionRun later = collectionRunRepository.saveAndFlush(new CollectionRun(DAY, 604, 451, 120_001, T0));
		em.clear();

		assertThat(collectionRunRepository.findFirstByInspectDayOrderByCompletedAtDescIdDesc(DAY).orElseThrow().getId())
				.isEqualTo(later.getId());
	}

	@Test
	void 같은_조사일의_이력이_없으면_빈_결과를_반환한다() {
		collectionRunRepository.saveAndFlush(new CollectionRun(OTHER_DAY, 604, 450, 120_000, T0));

		assertThat(collectionRunRepository.findFirstByInspectDayOrderByCompletedAtDescIdDesc(DAY)).isEmpty();
	}

	@Test
	void 가격이_하나도_없는_수집도_기록할_수_있다() {
		CollectionRun saved = collectionRunRepository.saveAndFlush(new CollectionRun(DAY, 604, 0, 0, T0));

		assertThat(saved.getId()).isNotNull();
	}

	/*
	 * PostgreSQL은 제약 위반 뒤 같은 트랜잭션의 다음 문장을 거부하므로, 거부 사례마다 테스트를 나눈다.
	 */

	@Test
	void 상품_수가_음수면_저장할_수_없다() {
		assertRejected(new CollectionRun(DAY, -1, 0, 0, T0));
	}

	@Test
	void 가격_보유_상품_수가_음수면_저장할_수_없다() {
		assertRejected(new CollectionRun(DAY, 604, -1, 0, T0));
	}

	@Test
	void 전체_가격_건수가_음수면_저장할_수_없다() {
		assertRejected(new CollectionRun(DAY, 604, 0, -1, T0));
	}

	@Test
	void 가격_보유_상품_수가_상품_수보다_많으면_저장할_수_없다() {
		assertRejected(new CollectionRun(DAY, 604, 605, 1_000, T0));
	}

	@Test
	void 전체_가격_건수가_가격_보유_상품_수보다_적으면_저장할_수_없다() {
		assertRejected(new CollectionRun(DAY, 604, 450, 449, T0));
	}

	@Test
	void 조사일은_비울_수_없다() {
		assertRejected(new CollectionRun(null, 604, 450, 120_000, T0));
	}

	@Test
	void 완료_시각은_비울_수_없다() {
		assertRejected(new CollectionRun(DAY, 604, 450, 120_000, null));
	}

	private void assertRejected(CollectionRun run) {
		assertThatThrownBy(() -> collectionRunRepository.saveAndFlush(run))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
