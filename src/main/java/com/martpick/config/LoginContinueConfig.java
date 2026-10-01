package com.martpick.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 머리글의 로그인 링크가 현재 화면으로 돌아올 수 있도록, 복귀를 허용하는 화면의 요청에 {@code loginContinue}를 싣는다.
 * Thymeleaf 3.1은 템플릿에서 요청 객체에 접근하지 못하게 막으므로 요청 속성으로 전달한다.
 * 값은 복귀 대상 후보일 뿐이며, 로그인 시 {@link ContinueUrl}이 다시 검증한다.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class LoginContinueConfig implements WebMvcConfigurer {

	public static final String ATTRIBUTE = "loginContinue";

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
				String path = request.getRequestURI().substring(request.getContextPath().length());
				String query = request.getQueryString();
				request.setAttribute(ATTRIBUTE, query == null ? path : path + "?" + query);
				return true;
			}
		}).addPathPatterns("/products", "/cart", "/compare");
	}
}
