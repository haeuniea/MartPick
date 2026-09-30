package com.martpick.cart;

/**
 * 장바구니 변경 요청을 반영할 수 없을 때 발생한다. 메시지는 사용자에게 그대로 안내한다.
 */
public class InvalidCartRequestException extends RuntimeException {

	public InvalidCartRequestException(String message) {
		super(message);
	}
}
