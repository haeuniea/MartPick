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

	public PublicDataApiException(Type type, String operation, String detail) {
		super("[" + type + "] " + operation + ": " + detail);
		this.type = type;
		this.operation = operation;
		this.detail = detail;
	}
}
