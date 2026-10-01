package com.martpick.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * 보안 설정. 비회원 화면과 로그인·회원가입 화면을 허용하고, 세션 기반 폼 로그인·로그아웃을 사용한다.
 * CSRF와 세션 고정 방어는 Spring Security 기본값(활성)을 유지한다.
 * 로그인해야만 볼 수 있는 화면이 없으므로, 허용하지 않은 경로는 폼 로그인 기본 동작(로그인 화면 이동) 대신 403으로 거부하고
 * 로그인 후 복귀는 저장된 요청이 아니라 {@link ContinueUrl}로만 처리한다.
 * HttpSecurity는 서블릿 웹 애플리케이션에서만 만들어지므로, 웹 서버 없는 수집 전용 실행에서는 등록하지 않는다.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class SecurityConfig {

	private static final String DEFAULT_SUCCESS_URL = "/products";

	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
						.requestMatchers("/", "/products", "/cart", "/cart/**", "/compare", "/login", "/signup", "/css/**", "/error")
						.permitAll()
						.anyRequest().denyAll())
				.formLogin(form -> form
						.loginPage("/login")
						.loginProcessingUrl("/login")
						.successHandler((request, response, authentication) -> {
							String target = ContinueUrl.resolve(request.getParameter("continue"));
							redirectStrategy.sendRedirect(request, response, target != null ? target : DEFAULT_SUCCESS_URL);
						})
						.failureHandler((request, response, exception) -> {
							String target = ContinueUrl.resolve(request.getParameter("continue"));
							String url = "/login?error" + (target == null ? ""
									: "&continue=" + URLEncoder.encode(target, StandardCharsets.UTF_8));
							redirectStrategy.sendRedirect(request, response, url);
						}))
				.addFilterBefore(new AuthenticatedLoginGuardFilter(), UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new Http403ForbiddenEntryPoint()))
				.requestCache(cache -> cache.requestCache(new NullRequestCache()))
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl("/products"));
		return http.build();
	}
}
