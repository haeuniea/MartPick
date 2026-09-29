package com.martpick.compare;

import lombok.Getter;

/**
 * 입력이 잘못되어 가격 비교를 진행하지 않았음을 나타낸다.
 * 정상적으로 비교했지만 대상 판매점이 없는 경우는 {@link ComparisonResult#status()}로 구분한다.
 */
@Getter
public class InvalidComparisonRequestException extends RuntimeException {

	/** 검증 순서대로 나열한다. */
	public enum Reason {
		EMPTY_CART,
		INVALID_CART_LINE,
		REGION_REQUIRED,
		QUANTITY_OUT_OF_RANGE,
		DUPLICATE_PRODUCT,
		UNKNOWN_REGION,
		UNKNOWN_PRODUCT
	}

	private final Reason reason;

	public InvalidComparisonRequestException(Reason reason, String message) {
		super("[" + reason + "] " + message);
		this.reason = reason;
	}
}
