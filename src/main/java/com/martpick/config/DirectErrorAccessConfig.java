package com.martpick.config;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /error}는 컨테이너가 오류를 넘겨 줄 때만 쓰는 내부 경로이다. 보안 설정이 오류 디스패치를 위해 이 경로를 허용하므로, 사용자가 주소로 직접
 * 열면(오류 정보가 없는 일반 요청) 기본 오류 처리가 상태 999를 500으로 바꿔 "일시적인 오류" 화면을 보여 주었다. 이 경우를 없는 주소와 같은 404로 응답한다.
 * 컨테이너가 오류를 넘겨 줄 때 싣는 오류 상태 속성이 있는 요청은 그대로 통과시켜 실제 오류 화면은 바뀌지 않는다.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class DirectErrorAccessConfig implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
				if (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) != null) {
					return true;
				}
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return false;
			}
		}).addPathPatterns("/error");
	}
}
