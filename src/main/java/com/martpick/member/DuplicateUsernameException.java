package com.martpick.member;

/**
 * 이미 가입된 아이디로 가입을 요청했을 때 발생한다.
 */
public class DuplicateUsernameException extends RuntimeException {

	public DuplicateUsernameException(String username) {
		super("이미 사용 중인 아이디입니다: " + username);
	}
}
