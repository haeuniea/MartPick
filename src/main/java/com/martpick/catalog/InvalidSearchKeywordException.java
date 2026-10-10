package com.martpick.catalog;

/**
 * 검색어에 사용할 수 없는 문자(제어 문자)가 있을 때 발생한다. 메시지는 사용자에게 그대로 안내한다.
 * PostgreSQL은 NUL(0x00)을 문자열로 저장·비교할 수 없어 그대로 조회하면 DB 오류(500)가 되므로, 조회 전에 거부한다.
 */
public class InvalidSearchKeywordException extends RuntimeException {

	public InvalidSearchKeywordException() {
		super("검색어에 사용할 수 없는 문자가 포함되어 있습니다.");
	}
}
