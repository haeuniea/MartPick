package com.martpick.compare;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ComparisonResultTest {

	private static final CompleteStore COMPLETE = new CompleteStore(1L, "마트", "주소", List.of(), 1000L);
	private static final PartialStore PARTIAL = new PartialStore(2L, "마트", "주소", List.of(), List.of(), 500L);

	@Test
	void 완전_비교_판매점이_있으면_COMPARED이다() {
		assertThat(result(List.of(COMPLETE), List.of(PARTIAL)).status()).isEqualTo(ComparisonStatus.COMPARED);
	}

	@Test
	void 부분_비교_판매점만_있으면_NO_COMPLETE_STORE이다() {
		assertThat(result(List.of(), List.of(PARTIAL)).status()).isEqualTo(ComparisonStatus.NO_COMPLETE_STORE);
	}

	@Test
	void 가격이_확인된_판매점이_없으면_NO_PRICED_STORE이다() {
		assertThat(result(List.of(), List.of()).status()).isEqualTo(ComparisonStatus.NO_PRICED_STORE);
	}

	private static ComparisonResult result(List<CompleteStore> complete, List<PartialStore> partial) {
		return new ComparisonResult(LocalDate.of(2026, 9, 25), "020100000", "서울특별시", complete, partial);
	}
}
