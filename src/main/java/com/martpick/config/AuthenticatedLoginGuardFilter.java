package com.martpick.config;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 이미 로그인한 사용자의 POST /login을 인증 필터에 넘기지 않는다.
 * Spring Security의 폼 로그인 필터는 기존 인증 여부와 관계없이 새 자격 증명으로 다시 인증하므로,
 * 막지 않으면 로그인한 채로 다른 계정으로 바뀌거나 세션이 교체된다.
 * CSRF 검사 뒤, 폼 로그인 필터 앞에 둔다.
 * 요청 경로는 문자열 비교가 아니라 Security의 경로 매처로 판단한다. 컨테이너가 디코딩한 경로로 로그인 필터가 처리하는
 * 요청(예: {@code /%6cogin})을 같은 기준으로 잡기 위해서다.
 */
class AuthenticatedLoginGuardFilter extends OncePerRequestFilter {

	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

	private final RequestMatcher loginSubmit = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !loginSubmit.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken)) {
			redirectStrategy.sendRedirect(request, response, "/products");
			return;
		}
		chain.doFilter(request, response);
	}
}
