package com.martpick.publicdata;

import lombok.Getter;

/**
 * 공공데이터 API 호출 실패. 정상적인 빈 결과는 이 예외가 아니라 빈 목록으로 표현한다.
 * 인증키가 포함될 수 있는 원본 예외는 cause로 연결하지 않는다.
 */
@Getter
public class PublicDataApiException extends RuntimeException {

	public enum Type {
		MISSING_SERVICE_KEY,
		NETWORK,
		HTTP_ERROR,
		PARSE_ERROR,
		API_ERROR,
		UNEXPECTED_STRUCTURE,
		INCOMPLETE_RESPONSE,
		INVALID_ROW,
		INCONSISTENT_ROW
	}

	private final Type type;
	private final String operation;
	private final String detail;
	/** HTTP 응답 상태 코드. HTTP 응답을 받지 못했거나 해당하지 않으면 0 */
	private final int httpStatus;

	public PublicDataApiException(Type type, String operation, String detail) {
		this(type, operation, detail, 0);
	}

	public PublicDataApiException(Type type, String operation, String detail, int httpStatus) {
		super("[" + type + "] " + operation + ": " + detail);
		this.type = type;
		this.operation = operation;
		this.detail = detail;
		this.httpStatus = httpStatus;
	}

	/**
	 * 일시적 오류(네트워크 오류·타임아웃, HTTP 5xx)만 재시도 대상이다 (PROJECT_SPEC 4-1-1).
	 * HTTP 429, 인증·요청 오류(4xx), resultCode 오류, 응답 형식·데이터 오류는 재시도하지 않는다.
	 */
	public boolean isRetryable() {
		return type == Type.NETWORK || (type == Type.HTTP_ERROR && httpStatus >= 500 && httpStatus < 600);
	}
}
