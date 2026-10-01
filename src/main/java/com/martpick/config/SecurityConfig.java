package com.martpick.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 설정. 비회원 화면과 회원가입 화면만 허용하고 CSRF는 기본값(활성)을 유지한다.
 * 로그인·로그아웃 설정은 5단계 인증 구현(5-2)에서 추가한다.
 * HttpSecurity는 서블릿 웹 애플리케이션에서만 만들어지므로, 웹 서버 없는 수집 전용 실행에서는 등록하지 않는다.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/products", "/cart", "/cart/**", "/compare", "/signup", "/css/**", "/error").permitAll()
				.anyRequest().denyAll());
		return http.build();
	}
}
