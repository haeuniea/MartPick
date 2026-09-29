package com.martpick.compare;

import java.time.LocalDate;
import java.util.List;

/**
 * @param inspectDay 비교에 사용한 기준 조사일
 * @param areaCode 선택한 시·도 코드
 * @param areaName 선택한 시·도 이름
 * @param completeStores 완전 비교 목록 (totalPrice 오름차순, 같으면 판매점 ID 오름차순)
 * @param partialStores 부분 비교 목록 (판매점 ID 오름차순)
 */
public record ComparisonResult(LocalDate inspectDay, String areaCode, String areaName,
		List<CompleteStore> completeStores, List<PartialStore> partialStores) {

	public ComparisonStatus status() {
		if (!completeStores.isEmpty()) {
			return ComparisonStatus.COMPARED;
		}
		return partialStores.isEmpty() ? ComparisonStatus.NO_PRICED_STORE : ComparisonStatus.NO_COMPLETE_STORE;
	}
}
