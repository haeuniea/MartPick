package com.martpick.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 Tomcat의 오류 디스패치까지 거쳐 브라우저가 받는 오류 화면과 HTTP 상태 코드를 확인한다.
 * MockMvc는 컨테이너의 오류 화면 전달을 수행하지 않으므로 실제 서버를 쓴다. 읽기 요청과 CSRF 없는 거부 요청만 보내므로 DB 데이터는 바뀌지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorPageWebServerTest {

	static final String NOT_FOUND_TITLE = "페이지를 찾을 수 없습니다";
	static final String FORBIDDEN_TITLE = "접근할 수 없습니다";
	static final String CLIENT_ERROR_TITLE = "요청을 처리할 수 없습니다";

	private static final List<String> INTERNAL_TERMS = List.of("Whitelabel", "Exception", "org.springframework",
			"com.martpick", "jdbc:", "postgresql", "stackTrace", "trace\"", "at java.");

	private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	@LocalServerPort
	private int port;

	@ParameterizedTest
	@ValueSource(strings = {"/nope", "/products/", "/admin", "/actuator/health", "/logout", "/login/", "/cart/unknown",
			"/css/none.css", "/%6cogin/x", "/error/x"})
	void 존재하지_않는_주소는_404와_한국어_안내를_보여_준다(String path) throws Exception {
		HttpResponse<String> response = get(path, "text/html");

		assertThat(response.statusCode()).as(path).isEqualTo(404);
		assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
		assertThat(response.body()).contains(NOT_FOUND_TITLE);
		assertNoInternalInformation(response.body());
	}

	@Test
	void CSRF_토큰이_없는_요청은_403과_한국어_안내를_보여_준다() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/cart/items"))
				.header("Accept", "text/html")
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString("goodId=1")));

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains(FORBIDDEN_TITLE);
		assertThat(response.body()).doesNotContain(NOT_FOUND_TITLE);
		assertNoInternalInformation(response.body());
	}

	@Test
	void 허용되지_않는_메서드는_405와_일반_안내를_보여_준다() throws Exception {
		HttpResponse<String> response = get("/cart/items", "text/html");

		assertThat(response.statusCode()).isEqualTo(405);
		assertThat(response.body()).contains(CLIENT_ERROR_TITLE).contains("405");
		assertNoInternalInformation(response.body());
	}

	@Test
	void 화면이_아닌_요청에는_JSON을_돌려주되_내부_정보는_담지_않는다() throws Exception {
		HttpResponse<String> response = get("/nope", "application/json");

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("json");
		assertThat(response.body()).doesNotContain("trace").doesNotContain("exception").doesNotContain("message\":\"No");
	}

	@Test
	void 오류_경로에_직접_접근하면_오류_화면이_아니라_404로_응답한다() throws Exception {
		HttpResponse<String> html = get("/error", "text/html");

		assertThat(html.statusCode()).isEqualTo(404);
		assertThat(html.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
		assertThat(html.body()).contains(NOT_FOUND_TITLE).doesNotContain("999").doesNotContain("일시적인 오류");
		assertNoInternalInformation(html.body());
	}

	@Test
	void 오류_경로에_직접_접근해도_JSON_요청에는_404_JSON을_돌려주고_내부_정보는_담지_않는다() throws Exception {
		HttpResponse<String> json = get("/error", "application/json");

		assertThat(json.statusCode()).isEqualTo(404);
		assertThat(json.headers().firstValue("Content-Type").orElse("")).contains("json");
		assertThat(json.body()).contains("404").doesNotContain("999").doesNotContain("trace").doesNotContain("exception");
	}

	@ParameterizedTest
	@ValueSource(strings = {"/error?x=1", "/error?status=500", "/error?status=404&error=Not+Found"})
	void 오류_경로에_쿼리를_붙여_접근해도_404이다(String path) throws Exception {
		assertThat(get(path, "text/html").statusCode()).as(path).isEqualTo(404);
	}

	private static void assertNoInternalInformation(String body) {
		for (String term : INTERNAL_TERMS) {
			assertThat(body).as("오류 화면에 내부 정보(" + term + ")가 없어야 한다").doesNotContain(term);
		}
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> get(String path, String accept) throws IOException, InterruptedException {
		return send(HttpRequest.newBuilder(uri(path)).header("Accept", accept).GET());
	}

	private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
		return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}
}
