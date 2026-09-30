package com.martpick.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 4단계(비회원 기능) 보안 설정. 비회원 화면만 허용하고 CSRF는 기본값(활성)을 유지한다.
 * 로그인 화면과 회원 경로는 5단계 인증 구현 시 추가한다.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/products", "/cart", "/cart/**", "/css/**", "/error").permitAll()
				.anyRequest().denyAll());
		return http.build();
	}
}
