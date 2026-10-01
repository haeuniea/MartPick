package com.martpick.member;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 회원가입 입력 (PROJECT_SPEC 2-4). 비밀번호는 앞뒤 공백을 자르지 않고, 확인 값은 저장하지 않는다.
 */
public record SignupForm(
		@NotNull(message = "아이디를 입력해 주세요.")
		@Pattern(regexp = "[A-Za-z0-9]{4,20}", message = "아이디는 영문·숫자 4~20자로 입력해 주세요.")
		String username,
		@NotNull(message = "비밀번호를 입력해 주세요.")
		String password,
		String passwordConfirm) {

	static final int PASSWORD_MIN_LENGTH = 8;
	static final int PASSWORD_MAX_BYTES = 72;

	@AssertTrue(message = "비밀번호는 8자 이상이어야 합니다.")
	public boolean isPasswordLongEnough() {
		return password == null || password.codePointCount(0, password.length()) >= PASSWORD_MIN_LENGTH;
	}

	@AssertTrue(message = "비밀번호는 72바이트 이하여야 합니다. 한글은 한 글자가 3바이트입니다.")
	public boolean isPasswordWithinByteLimit() {
		return password == null || password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
	}

	@AssertTrue(message = "비밀번호 확인이 일치하지 않습니다.")
	public boolean isPasswordConfirmed() {
		return password == null || password.equals(passwordConfirm);
	}

	@Override
	public String toString() {
		return "SignupForm[username=" + username + ", password=****, passwordConfirm=****]";
	}
}
