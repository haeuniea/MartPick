package com.martpick.collect;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 전체 상품 수집 결과 요약 (PROJECT_SPEC 4-1-1). 상품별 가격 건수는 모두 나열하지 않고 분포로 보고한다.
 *
 * @param productCount API 상품 응답 건수
 * @param priceQueriedProductCount 가격을 조회한 상품 수. 상품 응답 건수와 같아야 한다.
 * @param pricedProductCount 기준 조사일 가격이 1건 이상인 상품 수
 * @param baselinePriceCounts 기준 상품별 가격 건수
 * @param priceCountDistribution 상품별 가격 건수 분포 ("0", "1-99", "100-199" … → 상품 수)
 * @param missingProducts 상품 응답에 없는 기존 상품 ID와, 전체 교체로 삭제된 그 상품의 기준 조사일 가격 건수
 * @param g2Applied 전체 건수 급감 안전장치(G2) 적용 여부. 같은 조사일의 이전 성공 이력이 없으면 false
 * @param previousRunId G2 기준으로 쓴 이전 성공 이력 ID. 없으면 {@code null}
 * @param previousPriceCount 이전 성공 이력의 전체 가격 건수. 없으면 {@code null}
 * @param previousPricedProductCount 이전 성공 이력의 가격 보유 상품 수. 없으면 {@code null}
 * @param runId 이번 수집으로 기록한 성공 이력 ID
 * @param retries 일시적 오류로 재시도한 호출 구분과 재시도 횟수
 * @param warnings 저장을 막지는 않지만 사람이 확인해야 하는 참고 신호
 */
public record CollectionReport(
		LocalDate inspectDay,
		int regionCount,
		int storeCount,
		int excludedStoreCount,
		int productCount,
		int priceQueriedProductCount,
		int pricedProductCount,
		int totalPriceCount,
		Map<Long, Integer> baselinePriceCounts,
		Map<String, Integer> priceCountDistribution,
		Map<Long, Long> missingProducts,
		boolean g2Applied,
		Long previousRunId,
		Integer previousPriceCount,
		Integer previousPricedProductCount,
		long runId,
		Map<String, Integer> retries,
		List<String> warnings) {

	public int noPriceProductCount() {
		return productCount - pricedProductCount;
	}

	public int retryCount() {
		return retries.values().stream().mapToInt(Integer::intValue).sum();
	}
}
