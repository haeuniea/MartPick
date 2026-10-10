package com.martpick.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import jakarta.servlet.RequestDispatcher;

/**
 * 컨테이너가 전달하는 오류 정보를 직접 넣어 오류 화면의 종류(403, 404, 4xx, 5xx)와 내부 정보 비노출을 확인한다.
 * 500은 일부러 일으키기 어려우므로 서버 오류가 발생했을 때와 같은 요청 속성으로 {@code /error}를 호출한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorPageViewTest {

	private static final String SECRET = "jdbc:postgresql://db.internal:5432/martpick password=s3cret";

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest
	@ValueSource(ints = {500, 502, 503})
	void 서버_오류는_같은_상태_코드와_일시적_오류_안내를_보여_주고_내부_정보를_숨긴다(int statusCode) throws Exception {
		mockMvc.perform(errorRequest(statusCode))
				.andExpect(status().is(statusCode))
				.andExpect(content().string(containsString("일시적인 오류가 발생했습니다")))
				.andExpect(content().string(containsString(String.valueOf(statusCode))))
				.andExpect(content().string(not(containsString("s3cret"))))
				.andExpect(content().string(not(containsString("jdbc:"))))
				.andExpect(content().string(not(containsString("RuntimeException"))))
				.andExpect(content().string(not(containsString("Whitelabel"))));
	}

	@Test
	void 접근_거부는_403_안내를_보여_준다() throws Exception {
		mockMvc.perform(errorRequest(403))
				.andExpect(status().isForbidden())
				.andExpect(content().string(containsString(ErrorPageWebServerTest.FORBIDDEN_TITLE)))
				.andExpect(content().string(not(containsString(ErrorPageWebServerTest.NOT_FOUND_TITLE))));
	}

	@Test
	void 없는_페이지는_404_안내를_보여_준다() throws Exception {
		mockMvc.perform(errorRequest(404))
				.andExpect(status().isNotFound())
				.andExpect(content().string(containsString(ErrorPageWebServerTest.NOT_FOUND_TITLE)));
	}

	@ParameterizedTest
	@ValueSource(ints = {400, 405, 414})
	void 그_밖의_요청_오류는_같은_상태_코드와_일반_안내를_보여_준다(int statusCode) throws Exception {
		mockMvc.perform(errorRequest(statusCode))
				.andExpect(status().is(statusCode))
				.andExpect(content().string(containsString(ErrorPageWebServerTest.CLIENT_ERROR_TITLE)))
				.andExpect(content().string(containsString(String.valueOf(statusCode))));
	}

	@Test
	void 오류_화면에서도_마트픽_머리글과_상품_찾기_링크를_보여_준다() throws Exception {
		mockMvc.perform(errorRequest(404))
				.andExpect(content().string(containsString("마트픽")))
				.andExpect(content().string(containsString("href=\"/products\"")));
	}

	private static MockHttpServletRequestBuilder errorRequest(int statusCode) {
		return get("/error").accept(MediaType.TEXT_HTML)
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, statusCode)
				.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/products")
				.requestAttr(RequestDispatcher.ERROR_MESSAGE, SECRET)
				.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new RuntimeException(SECRET));
	}
}
