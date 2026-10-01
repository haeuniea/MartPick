package com.martpick.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class SignupFormTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void 올바른_입력은_검증을_통과한다() {
		assertThat(violations("tester01", "password1", "password1")).isEmpty();
	}

	@Test
	void 아이디는_영문_숫자_4자에서_20자까지_허용한다() {
		assertThat(violations("abcd", "password1", "password1")).isEmpty();
		assertThat(violations("a".repeat(20), "password1", "password1")).isEmpty();
		assertThat(violations("ABCD1234", "password1", "password1")).isEmpty();
	}

	@Test
	void 아이디가_3자이거나_21자이면_거부한다() {
		assertThat(violations("abc", "password1", "password1")).isNotEmpty();
		assertThat(violations("a".repeat(21), "password1", "password1")).isNotEmpty();
	}

	@Test
	void 아이디에_영문_숫자_외_문자가_있으면_거부한다() {
		for (String username : new String[] {"test_01", "test-01", "test 01", "tester!", "테스트유저", "test01 ", "ｔｅｓｔ０１", "test\n"}) {
			assertThat(violations(username, "password1", "password1")).as(username).isNotEmpty();
		}
	}

	@Test
	void 아이디가_비어_있거나_null이면_거부한다() {
		assertThat(violations("", "password1", "password1")).isNotEmpty();
		assertThat(violations(null, "password1", "password1")).isNotEmpty();
	}

	@Test
	void 비밀번호는_8자_이상이어야_한다() {
		assertThat(violations("tester01", "1234567", "1234567")).isNotEmpty();
		assertThat(violations("tester01", "12345678", "12345678")).isEmpty();
	}

	@Test
	void 비밀번호는_대문자나_특수문자를_강제하지_않는다() {
		assertThat(violations("tester01", "abcdefgh", "abcdefgh")).isEmpty();
	}

	@Test
	void 비밀번호의_앞뒤_공백은_그대로_허용하고_자르지_않는다() {
		assertThat(violations("tester01", "  pass  w", "  pass  w")).isEmpty();
	}

	@Test
	void 비밀번호는_72바이트까지_허용하고_73바이트이면_거부한다() {
		assertThat(violations("tester01", "a".repeat(72), "a".repeat(72))).isEmpty();
		assertThat(violations("tester01", "a".repeat(73), "a".repeat(73))).isNotEmpty();
	}

	@Test
	void 한글_비밀번호는_글자_수가_아니라_바이트로_제한한다() {
		assertThat(violations("tester01", "가".repeat(24), "가".repeat(24))).isEmpty();
		assertThat(violations("tester01", "가".repeat(25), "가".repeat(25))).isNotEmpty();
	}

	@Test
	void 비밀번호가_비어_있거나_null이면_거부한다() {
		assertThat(violations("tester01", "", "")).isNotEmpty();
		assertThat(violations("tester01", null, null)).isNotEmpty();
	}

	@Test
	void 비밀번호_확인이_다르면_거부한다() {
		assertThat(violations("tester01", "password1", "password2")).isNotEmpty();
		assertThat(violations("tester01", "password1", "")).isNotEmpty();
		assertThat(violations("tester01", "password1", null)).isNotEmpty();
	}

	@Test
	void toString에_비밀번호와_확인_값이_드러나지_않는다() {
		SignupForm form = new SignupForm("tester01", "secret-pw-123", "secret-confirm-456");

		assertThat(form.toString()).contains("tester01").doesNotContain("secret-pw-123").doesNotContain("secret-confirm-456");
	}

	private static Set<ConstraintViolation<SignupForm>> violations(String username, String password, String passwordConfirm) {
		return VALIDATOR.validate(new SignupForm(username, password, passwordConfirm));
	}
}
