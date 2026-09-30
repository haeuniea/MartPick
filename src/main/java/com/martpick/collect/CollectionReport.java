package com.martpick.collect;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * @param retries 일시적 오류로 재시도한 호출 구분과 재시도 횟수
 * @param warnings 저장을 막지는 않지만 사람이 확인해야 하는 참고 신호
 */
public record CollectionReport(
		LocalDate inspectDay,
		int regionCount,
		int storeCount,
		int excludedStoreCount,
		int productCount,
		Map<Long, Integer> priceCounts,
		int totalPriceCount,
		Map<String, Integer> retries,
		List<String> warnings) {

	public int retryCount() {
		return retries.values().stream().mapToInt(Integer::intValue).sum();
	}
}
