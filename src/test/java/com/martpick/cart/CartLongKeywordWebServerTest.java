package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 담기 후 이동 주소(Location 헤더)가 컨테이너의 헤더 크기 한도를 넘으면 빈 500이 되었다. MockMvc는 헤더 크기를 검사하지 않으므로 실제 Tomcat으로 확인한다.
 * 존재하지 않는 상품 ID로 요청해 이동 주소를 만드는 경로만 거치므로 DB 데이터는 바뀌지 않는다(세션 장바구니와 DB 모두 변경 없음).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CartLongKeywordWebServerTest {

	private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

	@LocalServerPort
	private int port;

	@Test
	void 아주_긴_검색어로_담기를_요청해도_빈_500이_아니라_짧은_주소로_이동한다() throws Exception {
		HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
				.followRedirects(HttpClient.Redirect.NEVER).build();
		String csrf = csrfToken(client);

		for (int length : new int[] {50, 51, 7_000, 8_300, 20_000, 200_000}) {
			HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/cart/items"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString("_csrf=" + csrf + "&goodId=-1&keyword="
							+ URLEncoder.encode("a".repeat(length), StandardCharsets.UTF_8) + "&page=1"))
					.build(), HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).as("keyword " + length + "자").isEqualTo(302);
			String location = response.headers().firstValue("Location").orElse("");
			assertThat(location).as("keyword " + length + "자").startsWith("http://localhost:" + port + "/products");
			assertThat(location.length()).as("keyword " + length + "자").isLessThan(300);
		}
	}

	private String csrfToken(HttpClient client) throws IOException, InterruptedException {
		String html = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).GET().build(),
				HttpResponse.BodyHandlers.ofString()).body();
		Matcher matcher = CSRF.matcher(html);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
