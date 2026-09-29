package com.martpick.compare;

import java.util.List;

/**
 * @param completeStores 완전 비교 목록 (totalPrice 오름차순, 같으면 판매점 ID 오름차순)
 * @param partialStores 부분 비교 목록 (판매점 ID 오름차순)
 */
public record ComparisonResult(List<CompleteStore> completeStores, List<PartialStore> partialStores) {

	public ComparisonStatus status() {
		if (!completeStores.isEmpty()) {
			return ComparisonStatus.COMPARED;
		}
		return partialStores.isEmpty() ? ComparisonStatus.NO_PRICED_STORE : ComparisonStatus.NO_COMPLETE_STORE;
	}
}
