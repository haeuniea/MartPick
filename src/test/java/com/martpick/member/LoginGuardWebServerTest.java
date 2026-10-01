package com.martpick.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 Tomcat과 HTTP 클라이언트로, 로그인한 사용자의 POST /login 차단이 인코딩된 경로 변형에서도 유지되는지 확인한다.
 * 컨테이너(Tomcat)가 요청 경로를 디코딩하는 실제 동작을 그대로 확인하려고 MockMvc 대신 실제 서버를 쓴다.
 * 서버 스레드가 따로 커밋하므로 {@code @Transactional} 롤백을 쓸 수 없어, 테스트 회원을 직접 지운다(테스트 DB 전용).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoginGuardWebServerTest {

	private static final String USER_A = "guardA1";
	private static final String USER_B = "guardB2";
	private static final String PASSWORD_A = "password-A-1234";
	private static final String PASSWORD_B = "password-B-5678";

	private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]*)\"");
	private static final Pattern SIGNED_IN_NAME = Pattern.compile("site-auth__name\"><strong>([^<]*)");

	@LocalServerPort
	private int port;

	@Autowired
	private MemberService memberService;

	@Autowired
	private MemberRepository memberRepository;

	@BeforeEach
	void setUp() {
		deleteTestMembers();
		memberService.signup(USER_A, PASSWORD_A);
		memberService.signup(USER_B, PASSWORD_B);
	}

	@AfterEach
	void deleteTestMembers() {
		for (String username : List.of(USER_A, USER_B)) {
			memberRepository.findByUsername(username).ifPresent(memberRepository::delete);
		}
	}

	@Test
	void 비로그인_사용자의_정상_로그인은_기존대로_성공한다() throws Exception {
		HttpClient client = newClient();

		HttpResponse<String> response = postLogin(client, "/login", USER_A, PASSWORD_A, csrf(client, "/login"));

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/products");
		assertThat(signedInName(client)).isEqualTo(USER_A);
	}

	@Test
	void 로그인한_A가_B의_자격_증명으로_POST_로그인을_보내면_차단되고_A가_유지된다() throws Exception {
		HttpClient client = loggedIn(USER_A, PASSWORD_A);

		HttpResponse<String> response = postLogin(client, "/login", USER_B, PASSWORD_B, csrf(client, "/products"));

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/products");
		assertThat(signedInName(client)).isEqualTo(USER_A);
	}

	@Test
	void 로그인한_A가_인코딩된_경로_POST_percent6cogin으로_B의_자격_증명을_보내도_차단되고_A가_유지된다() throws Exception {
		HttpClient client = loggedIn(USER_A, PASSWORD_A);

		HttpResponse<String> response = postLogin(client, "/%6cogin", USER_B, PASSWORD_B, csrf(client, "/products"));

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/products");
		assertThat(signedInName(client)).isEqualTo(USER_A);
	}

	@Test
	void 로그인_필터가_처리하는_모든_경로_변형은_로그인한_사용자에게도_차단된다() throws Exception {
		List<String> processedAnonymously = new ArrayList<>();
		List<String> switchedAccount = new ArrayList<>();

		for (String path : PATH_VARIANTS) {
			HttpClient anonymous = newClient();
			if (attemptLogin(anonymous, path, csrf(anonymous, "/login")) && USER_B.equals(signedInName(anonymous))) {
				processedAnonymously.add(path);
			}

			HttpClient signedIn = loggedIn(USER_A, PASSWORD_A);
			attemptLogin(signedIn, path, csrf(signedIn, "/products"));
			if (!USER_A.equals(signedInName(signedIn))) {
				switchedAccount.add(path);
			}
		}

		assertThat(processedAnonymously).as("로그인 필터가 실제로 처리하는 경로(검증이 의미 있는지 확인)")
				.contains("/login", "/%6cogin", "/lo%67in", "/%6C%6F%67%69%6E");
		assertThat(switchedAccount).as("로그인한 상태에서 계정이 바뀐 경로").isEmpty();
	}

	@Test
	void CSRF_토큰이_없으면_로그인_상태에서도_거부하고_A가_유지된다() throws Exception {
		HttpClient client = loggedIn(USER_A, PASSWORD_A);

		for (String path : List.of("/login", "/%6cogin")) {
			HttpResponse<String> response = send(client, HttpRequest.newBuilder(uri(path))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(form("username", USER_B, "password", PASSWORD_B))));
			assertThat(response.statusCode()).as(path).isEqualTo(403);
		}
		assertThat(signedInName(client)).isEqualTo(USER_A);
	}

	private static final List<String> PATH_VARIANTS = List.of(
			"/login", "/%6cogin", "/lo%67in", "/%6C%6F%67%69%6E", "/login?x=1", "/login/", "/LOGIN", "/%4cogin",
			"/login;x=1", "/login;jsessionid=abc", "/./login", "//login", "/login%2e", "/login%20");

	private boolean attemptLogin(HttpClient client, String path, String csrfToken) {
		try {
			postLogin(client, path, USER_B, PASSWORD_B, csrfToken);
			return true;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	private HttpClient loggedIn(String username, String password) throws Exception {
		HttpClient client = newClient();
		postLogin(client, "/login", username, password, csrf(client, "/login"));
		assertThat(signedInName(client)).isEqualTo(username);
		return client;
	}

	private static HttpClient newClient() {
		return HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER).build();
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> get(HttpClient client, String path) throws IOException, InterruptedException {
		return send(client, HttpRequest.newBuilder(uri(path)).GET());
	}

	private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request)
			throws IOException, InterruptedException {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private String csrf(HttpClient client, String path) throws IOException, InterruptedException {
		Matcher matcher = CSRF.matcher(get(client, path).body());
		assertThat(matcher.find()).as(path + " 화면의 CSRF 토큰").isTrue();
		return matcher.group(1);
	}

	private HttpResponse<String> postLogin(HttpClient client, String path, String username, String password,
			String csrfToken) throws IOException, InterruptedException {
		return send(client, HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(
						form("_csrf", csrfToken, "username", username, "password", password))));
	}

	private String signedInName(HttpClient client) throws IOException, InterruptedException {
		Matcher matcher = SIGNED_IN_NAME.matcher(get(client, "/products").body());
		return matcher.find() ? matcher.group(1) : null;
	}

	private static String form(String... namesAndValues) {
		List<String> pairs = new ArrayList<>();
		for (int i = 0; i < namesAndValues.length; i += 2) {
			pairs.add(URLEncoder.encode(namesAndValues[i], StandardCharsets.UTF_8) + "="
					+ URLEncoder.encode(namesAndValues[i + 1], StandardCharsets.UTF_8));
		}
		return String.join("&", pairs);
	}
}
