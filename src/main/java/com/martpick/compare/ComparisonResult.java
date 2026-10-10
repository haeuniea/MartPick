package com.martpick.compare;

import java.time.LocalDate;
import java.util.List;

/**
 * @param inspectDay 비교에 사용한 기준 조사일
 * @param areaCode 선택한 시·도 코드
 * @param areaName 선택한 시·도 이름
 * @param completeStores 완전 비교 목록 (totalPrice 오름차순, 같으면 판매점 ID 오름차순)
 * @param partialStores 부분 비교 목록 (판매점 ID 오름차순)
 * @param noPriceNationwide 장바구니 상품 중 기준 조사일에 비교 대상 판매점 어디에도 가격이 없는 상품 (장바구니에 담은 순서).
 *            안내용이며 비교 계산에는 쓰지 않는다.
 * @param noPriceInRegion 장바구니 상품 중 선택 지역에는 비교 대상 판매점 가격이 없지만 다른 지역에는 있는 상품 (장바구니에 담은 순서).
 *            안내용이며 비교 계산에는 쓰지 않는다.
 */
public record ComparisonResult(LocalDate inspectDay, String areaCode, String areaName,
		List<CompleteStore> completeStores, List<PartialStore> partialStores,
		List<ProductInfo> noPriceNationwide, List<ProductInfo> noPriceInRegion) {

	/** 가격 부재 안내 목록 없이 비교 결과만 만든다. 두 안내 목록은 비어 있다. */
	public ComparisonResult(LocalDate inspectDay, String areaCode, String areaName,
			List<CompleteStore> completeStores, List<PartialStore> partialStores) {
		this(inspectDay, areaCode, areaName, completeStores, partialStores, List.of(), List.of());
	}

	public ComparisonStatus status() {
		if (!completeStores.isEmpty()) {
			return ComparisonStatus.COMPARED;
		}
		return partialStores.isEmpty() ? ComparisonStatus.NO_PRICED_STORE : ComparisonStatus.NO_COMPLETE_STORE;
	}
}
