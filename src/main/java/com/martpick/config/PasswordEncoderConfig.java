package com.martpick.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 빈. 웹 서버 없는 수집 실행에서도 {@code MemberService}가 만들어지므로,
 * 웹 환경에만 등록되는 {@link SecurityConfig}와 분리해 조건 없이 등록한다.
 */
@Configuration
public class PasswordEncoderConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
