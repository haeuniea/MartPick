package com.martpick.config;

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 허용 목록({@code denyAll}) 밖이라 거부한 요청에 403과 404 중 알맞은 상태 코드를 돌려준다.
 * 접근을 허용할지는 Spring Security가 그대로 판단하며, 이 클래스는 이미 거부된 요청의 응답 상태만 고른다. 어떤 요청도 새로 허용하지 않는다.
 *
 * <ul>
 * <li>CSRF 검증 실패는 경로와 관계없이 항상 403이다.</li>
 * <li>그 외에는 이 경로를 처리하는 컨트롤러가 없으면 404(없는 주소), 컨트롤러가 있는데도 허용 목록에 없어 거부했다면 403이다.</li>
 * <li>컨트롤러가 있는지 확인할 수 없는 모든 경우(매핑 조회 실패, 경로 조건 없음 등)는 이전처럼 403으로 거부한다.</li>
 * </ul>
 */
class DeniedRequestHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

	DeniedRequestHandler(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
		this.handlerMappings = handlerMappings;
	}

	/** 로그인하지 않은 사용자의 요청이 거부된 경우 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
			throws IOException {
		respond(request, response);
	}

	/** 로그인한 사용자의 요청이 거부된 경우와 CSRF 검증 실패 */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
			throws IOException {
		if (exception instanceof CsrfException) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		respond(request, response);
	}

	private void respond(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.sendError(hasController(request) ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_NOT_FOUND);
	}

	/**
	 * 이 요청의 경로를 처리하는 컨트롤러 매핑이 있는지 확인한다. 정적 리소스 핸들러는 모든 경로와 맞으므로 컨트롤러 매핑만 본다.
	 * 확인하려고 요청에 임시로 저장한 파싱된 경로 속성은 원래 값으로 되돌린다.
	 */
	private boolean hasController(HttpServletRequest request) {
		RequestMappingHandlerMapping mapping = handlerMappings.getIfUnique();
		if (mapping == null) {
			return true;
		}
		Object previousPath = request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
		try {
			ServletRequestPathUtils.parseAndCache(request);
			return mapping.getHandlerMethods().keySet().stream().anyMatch(info -> {
				PathPatternsRequestCondition paths = info.getPathPatternsCondition();
				return paths == null || paths.getMatchingCondition(request) != null;
			});
		} catch (RuntimeException e) {
			return true;
		} finally {
			if (previousPath == null) {
				request.removeAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
			} else {
				request.setAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE, previousPath);
			}
		}
	}
}
