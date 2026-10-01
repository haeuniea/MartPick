package com.martpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.martpick.config.ContinueUrl;

import jakarta.servlet.http.HttpSession;

/**
 * 실제 Spring Security 필터 체인과 PostgreSQL을 거치는 로그인·로그아웃 흐름.
 * 테스트 회원은 트랜잭션 롤백으로 남기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginFlowTest {

	private static final String PASSWORD_A = "password-A-1234";
	private static final String PASSWORD_B = "password-B-5678";
	private static final String LOGIN_ERROR = "/login?error";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberService memberService;

	@Autowired
	private MemberRepository memberRepository;

	@BeforeEach
	void setUp() {
		memberService.signup("loginA1", PASSWORD_A);
		memberService.signup("loginB2", PASSWORD_B);
	}

	@Test
	void 로그인에_성공하면_상품_탐색으로_이동하고_세션에_회원_ID가_담긴다() throws Exception {
		MvcResult result = mockMvc.perform(loginRequest("loginA1", PASSWORD_A, null))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/products"))
				.andReturn();

		MemberUserDetails principal = principalOf(sessionOf(result));
		assertThat(principal).isNotNull();
		assertThat(principal.getMemberId()).isEqualTo(memberRepository.findByUsername("loginA1").orElseThrow().getId());
		assertThat(principal.getUsername()).isEqualTo("loginA1");
	}

	@Test
	void 로그인_후_세션에는_비밀번호_해시가_남지_않는다() throws Exception {
		MvcResult result = mockMvc.perform(loginRequest("loginA1", PASSWORD_A, null)).andReturn();

		assertThat(principalOf(sessionOf(result)).getPassword()).isNull();
	}

	@Test
	void 허용된_continue_화면으로_복귀한다() throws Exception {
		mockMvc.perform(loginRequest("loginA1", PASSWORD_A, "/cart")).andExpect(redirectedUrl("/cart"));
		mockMvc.perform(loginRequest("loginA1", PASSWORD_A, "/products?keyword=%ED%96%87%EB%B0%98&page=2"))
				.andExpect(redirectedUrl("/products?keyword=%ED%96%87%EB%B0%98&page=2"));
		mockMvc.perform(loginRequest("loginA1", PASSWORD_A, "/compare?areaCode=020100000"))
				.andExpect(redirectedUrl("/compare?areaCode=020100000"));
	}

	@Test
	void 외부_URL과_위험한_continue는_무시하고_상품_탐색으로_이동한다() throws Exception {
		for (String value : new String[] {
				"https://evil.com", "//evil.com", "/\\evil.com", "javascript:alert(1)", "/cart/items", "/login", "/admin",
				"/products#x", "" }) {
			mockMvc.perform(loginRequest("loginA1", PASSWORD_A, value))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/products"));
		}
	}

	@Test
	void 없는_아이디와_틀린_비밀번호는_같은_응답이고_로그인되지_않는다() throws Exception {
		MvcResult unknownUser = mockMvc.perform(loginRequest("nobody1", PASSWORD_A, null))
				.andExpect(redirectedUrl(LOGIN_ERROR)).andReturn();
		MvcResult wrongPassword = mockMvc.perform(loginRequest("loginA1", "wrong-password-1", null))
				.andExpect(redirectedUrl(LOGIN_ERROR)).andReturn();

		assertThat(unknownUser.getResponse().getStatus()).isEqualTo(wrongPassword.getResponse().getStatus());
		assertThat(principalOf(unknownUser.getRequest().getSession(false))).isNull();
		assertThat(principalOf(wrongPassword.getRequest().getSession(false))).isNull();
	}

	@Test
	void 로그인_실패_화면은_같은_메시지를_보여주고_아이디_존재_여부를_드러내지_않는다() throws Exception {
		String unknown = mockMvc.perform(get("/login").param("error", "")).andReturn().getResponse().getContentAsString();

		assertThat(unknown).contains("아이디 또는 비밀번호가 올바르지 않습니다.");
		assertThat(unknown).doesNotContain("존재하지").doesNotContain("loginA1");
	}

	@Test
	void 로그인_실패해도_유효한_continue는_유지하고_위험한_값은_버린다() throws Exception {
		mockMvc.perform(loginRequest("loginA1", "wrong-password-1", "/cart"))
				.andExpect(redirectedUrl("/login?error&continue=%2Fcart"));
		mockMvc.perform(loginRequest("nobody1", PASSWORD_A, "https://evil.com"))
				.andExpect(redirectedUrl(LOGIN_ERROR));
	}

	@Test
	void 세션_고정_방어로_로그인하면_세션_ID가_바뀌고_기존_세션_속성은_유지된다() throws Exception {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute("beforeLogin", "kept");
		String before = session.getId();

		MvcResult result = mockMvc.perform(loginRequest("loginA1", PASSWORD_A, null).session(session)).andReturn();

		MockHttpSession after = sessionOf(result);
		assertThat(after.getId()).isNotEqualTo(before);
		assertThat(after.getAttribute("beforeLogin")).isEqualTo("kept");
		assertThat(principalOf(after)).isNotNull();
	}

	@Test
	void CSRF_토큰이_없는_로그인_요청은_거부한다() throws Exception {
		MvcResult result = mockMvc.perform(post("/login").param("username", "loginA1").param("password", PASSWORD_A))
				.andExpect(status().isForbidden()).andReturn();

		assertThat(principalOf(result.getRequest().getSession(false))).isNull();
	}

	@Test
	void 로그아웃은_POST와_CSRF로_세션을_끝내고_상품_탐색으로_이동한다() throws Exception {
		MockHttpSession session = loggedIn("loginA1", PASSWORD_A);

		mockMvc.perform(post("/logout").session(session).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/products"));

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void CSRF_토큰이_없는_로그아웃은_거부하고_로그인_상태가_유지된다() throws Exception {
		MockHttpSession session = loggedIn("loginA1", PASSWORD_A);

		mockMvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());

		assertThat(session.isInvalid()).isFalse();
		assertThat(principalOf(session)).isNotNull();
	}

	@Test
	void GET_로그아웃은_허용하지_않고_로그인_상태가_유지된다() throws Exception {
		MockHttpSession session = loggedIn("loginA1", PASSWORD_A);

		mockMvc.perform(get("/logout").session(session)).andExpect(status().isForbidden());

		assertThat(session.isInvalid()).isFalse();
		assertThat(principalOf(session)).isNotNull();
	}

	@Test
	void 로그인한_사용자가_GET_로그인과_GET_가입_화면에_접근하면_상품_탐색으로_이동한다() throws Exception {
		MockHttpSession session = loggedIn("loginA1", PASSWORD_A);

		mockMvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/products"));
		mockMvc.perform(get("/signup").session(session)).andExpect(redirectedUrl("/products"));
	}

	@Test
	void 로그인_상태의_POST_가입은_회원을_만들지_않는다() throws Exception {
		MockHttpSession session = loggedIn("loginA1", PASSWORD_A);
		long before = memberRepository.count();

		mockMvc.perform(post("/signup").session(session).with(csrf())
						.param("username", "loginC3").param("password", "password-C-9999").param("passwordConfirm", "password-C-9999"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/products"));

		assertThat(memberRepository.count()).isEqualTo(before);
		assertThat(memberRepository.existsByUsername("loginC3")).isFalse();
		assertThat(principalOf(session).getUsername()).isEqualTo("loginA1");
	}

	@Test
	void 로그인_상태의_POST_로그인은_다른_계정으로_바꾸거나_세션을_바꾸지_않는다() throws Exception {
		MockHttpSession session = loggedIn("loginA1", PASSWORD_A);
		String sessionId = session.getId();

		mockMvc.perform(loginRequest("loginB2", PASSWORD_B, "/cart").session(session))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/products"));
		mockMvc.perform(loginRequest("loginB2", "wrong-password-1", null).session(session))
				.andExpect(redirectedUrl("/products"));

		assertThat(session.getId()).isEqualTo(sessionId);
		assertThat(principalOf(session).getUsername()).isEqualTo("loginA1");
	}

	@Test
	void 로그인_화면은_고정_안내_문구를_보여주고_위험한_continue는_출력하지_않는다() throws Exception {
		String html = mockMvc.perform(get("/login").param("continue", "https://evil.com/x"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(html).contains("비회원으로 담은 상품은 로그인 후 장바구니에 자동으로 추가되지 않습니다.");
		assertThat(html).doesNotContain("evil.com").doesNotContain("Please sign in");
		assertThat(html).contains("name=\"_csrf\"", "name=\"username\"", "name=\"password\"");
	}

	@Test
	void 로그인_화면은_허용된_continue만_숨은_입력으로_유지한다() throws Exception {
		String html = mockMvc.perform(get("/login").param("continue", "/cart"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).containsPattern("name=\"continue\"[^>]*value=\"/cart\"|value=\"/cart\"[^>]*name=\"continue\"");
	}

	@Test
	void 비회원_화면의_머리글에는_현재_화면으로_돌아오는_로그인_링크와_가입_링크가_있다() throws Exception {
		String html = mockMvc.perform(get("/products"))
				.andReturn().getResponse().getContentAsString();

		assertThat(loginLinkContinue(html)).isEqualTo("/products");
		assertThat(html).contains("href=\"/signup\"").doesNotContain("로그아웃");
	}

	@Test
	void 검색어가_있는_화면에서는_검색어를_유지하는_로그인_링크를_만든다() throws Exception {
		String html = mockMvc.perform(get(URI.create("/products?keyword=%ED%96%87%EB%B0%98&page=2")))
				.andReturn().getResponse().getContentAsString();

		String carried = loginLinkContinue(html);
		assertThat(carried).isEqualTo("/products?keyword=%ED%96%87%EB%B0%98&page=2");
		assertThat(ContinueUrl.resolve(carried)).isEqualTo(carried);
	}

	@Test
	void 로그인_화면과_가입_화면의_로그인_링크는_continue를_붙이지_않는다() throws Exception {
		assertThat(mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString())
				.contains("href=\"/login\"").doesNotContain("/login?continue=");
		assertThat(mockMvc.perform(get("/signup")).andReturn().getResponse().getContentAsString())
				.contains("href=\"/login\"").doesNotContain("/login?continue=");
	}

	@Test
	void 로그인한_사용자의_머리글에는_아이디와_CSRF가_포함된_로그아웃_폼이_있다() throws Exception {
		MockHttpSession session = loggedIn("loginA1", PASSWORD_A);

		String html = mockMvc.perform(get("/products").session(session))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("loginA1", "로그아웃").doesNotContain("/login?continue=");
		assertThat(html).containsPattern("<form[^>]*action=\"/logout\"[^>]*method=\"post\"");
		assertThat(html).contains("name=\"_csrf\"");
	}

	@Test
	void 특수문자_검색어는_로그인_왕복에서_변형되거나_이중_인코딩되지_않는다() throws Exception {
		String keyword = "A&B #1+1 100% 우유";
		String query = "keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&page=2";

		String productsPage = mockMvc.perform(get(URI.create("/products?" + query)))
				.andReturn().getResponse().getContentAsString();
		String fromHeaderLink = loginLinkContinue(productsPage);
		assertThat(fromHeaderLink).isEqualTo("/products?" + query);

		String loginPage = mockMvc.perform(get("/login").param("continue", fromHeaderLink))
				.andReturn().getResponse().getContentAsString();
		String hidden = hiddenContinue(loginPage);
		assertThat(hidden).isEqualTo("/products?" + query);

		String failureUrl = mockMvc.perform(loginRequest("loginA1", "wrong-password-1", hidden))
				.andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
		String afterFailure = URLDecoder.decode(
				UriComponentsBuilder.fromUriString(failureUrl).build().getQueryParams().getFirst("continue"),
				StandardCharsets.UTF_8);
		assertThat(afterFailure).isEqualTo("/products?" + query);

		String retried = hiddenContinue(mockMvc.perform(get("/login").param("continue", afterFailure).param("error", ""))
				.andReturn().getResponse().getContentAsString());
		String successUrl = mockMvc.perform(loginRequest("loginA1", PASSWORD_A, retried))
				.andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
		assertThat(successUrl).isEqualTo("/products?" + query);

		String landedKeyword = URLDecoder.decode(
				UriComponentsBuilder.fromUriString(successUrl).build().getQueryParams().getFirst("keyword"),
				StandardCharsets.UTF_8);
		assertThat(landedKeyword).isEqualTo(keyword);
	}

	@Test
	void 가격_비교_화면으로_복귀하면_지역_코드가_유지된다() throws Exception {
		String successUrl = mockMvc.perform(loginRequest("loginA1", PASSWORD_A, "/compare?areaCode=020100000&evil=1"))
				.andReturn().getResponse().getRedirectedUrl();

		assertThat(successUrl).isEqualTo("/compare?areaCode=020100000");
	}

	private static String hiddenContinue(String html) {
		Matcher matcher = Pattern.compile("name=\"continue\" value=\"([^\"]*)\"").matcher(html);
		assertThat(matcher.find()).as("숨은 continue 입력").isTrue();
		return matcher.group(1).replace("&amp;", "&");
	}

	private static String loginLinkContinue(String html) {
		Matcher matcher = Pattern.compile("href=\"/login\\?continue=([^\"]*)\"").matcher(html);
		assertThat(matcher.find()).as("continue가 붙은 로그인 링크").isTrue();
		return URLDecoder.decode(matcher.group(1).replace("&amp;", "&"), StandardCharsets.UTF_8);
	}

	private MockHttpServletRequestBuilder loginRequest(String username, String password, String continueUrl) {
		MockHttpServletRequestBuilder request = post("/login").with(csrf())
				.param("username", username).param("password", password);
		return continueUrl == null ? request : request.param("continue", continueUrl);
	}

	private MockHttpSession loggedIn(String username, String password) throws Exception {
		MvcResult result = mockMvc.perform(loginRequest(username, password, null))
				.andExpect(redirectedUrl("/products")).andReturn();
		return sessionOf(result);
	}

	private static MockHttpSession sessionOf(MvcResult result) {
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static MemberUserDetails principalOf(HttpSession session) {
		if (session == null) {
			return null;
		}
		Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		if (!(context instanceof SecurityContext securityContext) || securityContext.getAuthentication() == null) {
			return null;
		}
		return (MemberUserDetails) securityContext.getAuthentication().getPrincipal();
	}
}
