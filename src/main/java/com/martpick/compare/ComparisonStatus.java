package com.martpick.compare;

public enum ComparisonStatus {
	/** 완전 비교 판매점이 1곳 이상 */
	COMPARED,
	/** 완전 비교 판매점은 없고 부분 비교 판매점만 있음 */
	NO_COMPLETE_STORE,
	/** 장바구니 상품 가격이 확인된 판매점이 없음 */
	NO_PRICED_STORE
}
