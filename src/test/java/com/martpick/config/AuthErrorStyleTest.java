package com.martpick.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 가입 화면의 오류 목록(ul.alert.auth-errors)은 공통 .alert의 가로 배치(flex)를 그대로 받으면 오류들이 좁은 열로 나란히 놓여 모바일에서 읽기 어렵다.
 * 브라우저 없이 확인할 수 있는 최소한의 회귀 방지로, 해당 규칙이 목록을 세로(block)로 되돌리는지 CSS를 직접 확인한다.
 */
class AuthErrorStyleTest {

	@Test
	void 가입_오류_목록은_세로로_배치한다() throws IOException {
		String rule = rule(".auth-errors");

		assertThat(rule).containsPattern("display:\\s*block");
		assertThat(rule).doesNotContainPattern("display:\\s*flex");
	}

	private static String rule(String selector) throws IOException {
		String css;
		try (InputStream in = new ClassPathResource("static/css/main.css").getInputStream()) {
			css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)}").matcher(css);
		assertThat(matcher.find()).as(selector + " 규칙").isTrue();
		return matcher.group(1);
	}
}
