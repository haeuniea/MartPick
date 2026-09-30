package com.martpick.collect;

/**
 * 수집한 응답이 필수 검증(V1~V6)이나 가격 급감 안전장치를 통과하지 못했거나,
 * 재시도 총합 상한에 도달해 저장을 중단했음을 나타낸다.
 */
public class DataCollectionException extends RuntimeException {

	public DataCollectionException(String message) {
		super(message);
	}
}
