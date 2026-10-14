package com.martpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.martpick.config.SecurityConfig;

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MemberService memberService;

	@Test
	void 가입_화면은_로그인_없이_볼_수_있고_CSRF_토큰과_세_입력란을_가진다() throws Exception {
		String html = mockMvc.perform(get("/signup"))
				.andExpect(status().isOk())
				.andExpect(view().name("signup"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("name=\"_csrf\"", "name=\"username\"", "name=\"password\"", "name=\"passwordConfirm\"");
		assertThat(html).containsPattern("<input[^>]*type=\"password\"[^>]*name=\"password\"");
		assertThat(html).containsPattern("<input[^>]*type=\"password\"[^>]*name=\"passwordConfirm\"");
	}

	@Test
	void 가입_화면의_안내_문구는_간결하고_바이트_설명이나_대소문자_설명이_없다() throws Exception {
		String html = mockMvc.perform(get("/signup")).andReturn().getResponse().getContentAsString();

		assertThat(html).contains("영문·숫자 4~20자", "8자 이상 입력해 주세요.");
		assertThat(html).doesNotContain("바이트").doesNotContain("대소문자");
	}

	@Test
	void 가입_화면_하단에_로그인_화면으로_가는_링크가_있다() throws Exception {
		String html = mockMvc.perform(get("/signup")).andReturn().getResponse().getContentAsString();

		assertThat(html).containsPattern("이미 회원이신가요\\?\\s*<a[^>]*href=\"/login\"[^>]*>로그인</a>");
	}

	@Test
	void 비밀번호가_72바이트를_넘으면_이해하기_쉬운_메시지를_보여주고_값은_다시_표시하지_않는다() throws Exception {
		String tooLong = "p".repeat(73);

		String html = mockMvc.perform(signup("tester01", tooLong, tooLong))
				.andExpect(status().isOk()).andExpect(view().name("signup"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("비밀번호가 너무 깁니다. 입력 내용을 줄여 주세요.").doesNotContain(tooLong);
		then(memberService).should(never()).signup(any(), any());
	}

	@Test
	void 비밀번호_확인이_다르면_일치하지_않는다는_메시지를_보여준다() throws Exception {
		String html = mockMvc.perform(signup("tester01", "unique-pw-9x", "unique-confirm-8y"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("비밀번호 확인이 일치하지 않습니다.");
	}

	@Test
	void 검증_오류가_여러_개여도_아이디_비밀번호_비밀번호_확인_순서로_항목마다_표시한다() throws Exception {
		String username = "아이디는 영문·숫자 4~20자로 입력해 주세요.";
		String length = "비밀번호는 8자 이상이어야 합니다.";
		String tooLong = "비밀번호가 너무 깁니다. 입력 내용을 줄여 주세요.";
		String confirm = "비밀번호 확인이 일치하지 않습니다.";

		for (int i = 0; i < 3; i++) {
			assertOrdered(signupHtml("ab", "short", "different1"), username, length, confirm);
			assertOrdered(signupHtml("ab", "valid-password-1", "different-password"), username, confirm);
			assertOrdered(signupHtml("tester01", "short", "different1"), length, confirm);
			assertOrdered(signupHtml("ab", "p".repeat(73), "q".repeat(73)), username, tooLong);
			assertOrdered(signupHtml("tester01", "p".repeat(73), "q".repeat(73)), tooLong, confirm);
			assertOrdered(signupHtml("ab", "short", "short"), username, length);
		}
	}

	@Test
	void 검증_오류는_가로_나열이_아니라_세로_목록으로_표시한다() throws Exception {
		String html = signupHtml("ab", "short", "different1");

		assertThat(html).containsPattern("<ul[^>]*class=\"[^\"]*auth-errors[^\"]*\"");
		assertThat(html.split("<li>", -1).length - 1).as("오류 항목 수").isEqualTo(3);
	}

	private String signupHtml(String username, String password, String confirm) throws Exception {
		return mockMvc.perform(signup(username, password, confirm)).andReturn().getResponse().getContentAsString();
	}

	private static void assertOrdered(String html, String... messages) {
		int previous = -1;
		for (String message : messages) {
			int index = html.indexOf(message);
			assertThat(index).as(message).isGreaterThan(previous);
			previous = index;
		}
	}

	@Test
	void 가입에_성공하면_로그인_화면으로_이동하고_안내한다() throws Exception {
		mockMvc.perform(signup("tester01", "password1", "password1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"))
				.andExpect(flash().attributeExists("message"));

		then(memberService).should().signup("tester01", "password1");
	}

	@Test
	void 입력이_올바르지_않으면_서비스를_호출하지_않고_가입_화면을_다시_보여준다() throws Exception {
		mockMvc.perform(signup("ab", "short", "short"))
				.andExpect(status().isOk())
				.andExpect(view().name("signup"));

		then(memberService).should(never()).signup(anyString(), anyString());
	}

	@Test
	void 비밀번호_확인이_다르면_가입하지_않는다() throws Exception {
		mockMvc.perform(signup("tester01", "password1", "password2"))
				.andExpect(status().isOk())
				.andExpect(view().name("signup"));

		then(memberService).should(never()).signup(any(), any());
	}

	@Test
	void 검증에_실패해도_입력한_비밀번호와_확인_값을_화면에_다시_표시하지_않는다() throws Exception {
		String html = mockMvc.perform(signup("ab", "unique-pw-9x", "unique-confirm-8y"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(html).doesNotContain("unique-pw-9x").doesNotContain("unique-confirm-8y");
	}

	@Test
	void 검증에_실패하면_입력한_아이디는_다시_표시한다() throws Exception {
		String html = mockMvc.perform(signup("ab", "password1", "password1"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("value=\"ab\"");
	}

	@Test
	void 중복_아이디이면_안내하고_비밀번호는_다시_표시하지_않는다() throws Exception {
		willThrow(new DuplicateUsernameException("tester01")).given(memberService).signup("tester01", "unique-pw-9x");

		String html = mockMvc.perform(signup("tester01", "unique-pw-9x", "unique-pw-9x"))
				.andExpect(status().isOk())
				.andExpect(view().name("signup"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("이미 사용 중인 아이디입니다.").doesNotContain("unique-pw-9x");
	}

	@Test
	void CSRF_토큰이_없는_가입_요청은_거부한다() throws Exception {
		mockMvc.perform(post("/signup")
						.param("username", "tester01").param("password", "password1").param("passwordConfirm", "password1"))
				.andExpect(status().isForbidden());

		then(memberService).should(never()).signup(any(), any());
	}

	@Test
	void 로그인_화면은_로그인_없이_볼_수_있고_고정_안내_문구를_보여준다() throws Exception {
		String html = mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(view().name("login"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("비회원으로 담은 상품은 로그인 후 장바구니에 자동으로 추가되지 않습니다.");
		assertThat(html).doesNotContain("아이디 또는 비밀번호가 올바르지 않습니다.");
		assertThat(html).containsPattern("<input[^>]*type=\"password\"[^>]*name=\"password\"");
	}

	@Test
	void 로그인_실패_표시가_있으면_아이디_존재_여부를_구분하지_않는_한_가지_메시지를_보여준다() throws Exception {
		String html = mockMvc.perform(get("/login").param("error", ""))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("아이디 또는 비밀번호가 올바르지 않습니다.");
	}

	@Test
	void 로그인_화면은_허용된_continue만_숨은_입력으로_유지한다() throws Exception {
		String valid = mockMvc.perform(get("/login").param("continue", "/products?page=2"))
				.andReturn().getResponse().getContentAsString();
		String invalid = mockMvc.perform(get("/login").param("continue", "//evil.com"))
				.andReturn().getResponse().getContentAsString();

		assertThat(valid).contains("value=\"/products?page=2\"");
		assertThat(invalid).doesNotContain("evil.com").doesNotContain("name=\"continue\"");
	}

	@Test
	void 가입_화면은_비회원_장바구니가_자동으로_추가되지_않는다는_안내를_보여_준다() throws Exception {
		mockMvc.perform(get("/signup")).andExpect(status().isOk())
				.andExpect(content().string(containsString("비회원으로 담은 상품은 가입 후 로그인해도 장바구니에 자동으로 추가되지 않습니다.")));
	}

	@Test
	void 가입_화면은_허용된_continue만_숨은_입력과_로그인_링크로_유지한다() throws Exception {
		String html = mockMvc.perform(get("/signup").param("continue", "/cart"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(html).contains("name=\"continue\" value=\"/cart\"");
		assertThat(html).contains("<a href=\"/login?continue=/cart\">로그인</a>");
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://evil.com", "//evil.com", "/admin", "/login", "javascript:alert(1)", "%2Fcart",
			"/cart\r\nSet-Cookie: x=1"})
	void 가입_화면은_허용되지_않은_continue를_버리고_일반_로그인_링크를_쓴다(String tampered) throws Exception {
		String html = mockMvc.perform(get("/signup").param("continue", tampered))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(html).doesNotContain("name=\"continue\"").doesNotContain("evil.com");
		assertThat(html).contains("<a href=\"/login\">로그인</a>");
	}

	@Test
	void 가입에_성공하면_허용된_continue를_붙여_로그인_화면으로_이동한다() throws Exception {
		String redirect = mockMvc.perform(post("/signup").with(csrf())
						.param("username", "tester01").param("password", "password1").param("passwordConfirm", "password1")
						.param("continue", "/cart"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("message", "회원가입이 완료되었습니다. 로그인해 주세요."))
				.andReturn().getResponse().getRedirectedUrl();

		assertThat(redirect).startsWith("/login?continue=");
		assertThat(URLDecoder.decode(redirect.substring("/login?continue=".length()), StandardCharsets.UTF_8)).isEqualTo("/cart");
		then(memberService).should().signup("tester01", "password1");
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://evil.com", "//evil.com", "/admin", "javascript:alert(1)", "/cart\r\nX: y"})
	void 가입에_성공해도_허용되지_않은_continue는_붙이지_않는다(String tampered) throws Exception {
		mockMvc.perform(post("/signup").with(csrf())
						.param("username", "tester01").param("password", "password1").param("passwordConfirm", "password1")
						.param("continue", tampered))
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void 가입에_성공해도_continue가_없으면_이전처럼_로그인_화면으로_이동한다() throws Exception {
		mockMvc.perform(post("/signup").with(csrf())
						.param("username", "tester01").param("password", "password1").param("passwordConfirm", "password1"))
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void 입력이_잘못되거나_중복_아이디여도_허용된_continue는_가입_화면에_유지한다() throws Exception {
		String invalid = mockMvc.perform(post("/signup").with(csrf())
						.param("username", "ab").param("password", "password1").param("passwordConfirm", "password1")
						.param("continue", "/cart"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(invalid).contains("name=\"continue\" value=\"/cart\"");

		willThrow(new DuplicateUsernameException("tester01")).given(memberService).signup("tester01", "password1");
		String duplicate = mockMvc.perform(post("/signup").with(csrf())
						.param("username", "tester01").param("password", "password1").param("passwordConfirm", "password1")
						.param("continue", "/compare?areaCode=020100000"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(duplicate).contains("name=\"continue\" value=\"/compare?areaCode=020100000\"");
	}

	@Test
	void 로그인_화면의_회원가입_링크는_허용된_continue를_유지한다() throws Exception {
		String html = mockMvc.perform(get("/login").param("continue", "/cart"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(html).contains("<a href=\"/signup?continue=/cart\">회원가입</a>");
		assertThat(mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString())
				.contains("<a href=\"/signup\">회원가입</a>");
	}

	@Test
	void 로그인한_사용자의_가입_요청은_continue가_있어도_가입하지_않고_상품_탐색으로_이동한다() throws Exception {
		mockMvc.perform(post("/signup").with(user(new MemberUserDetails(7L, "tester01", "hash"))).with(csrf())
						.param("username", "other0001").param("password", "password1").param("passwordConfirm", "password1")
						.param("continue", "/cart"))
				.andExpect(redirectedUrl("/products"));

		then(memberService).should(never()).signup(anyString(), anyString());
	}

	@Test
	void 로그인한_사용자는_로그인과_가입_화면_대신_상품_탐색으로_이동한다() throws Exception {
		MemberUserDetails member = new MemberUserDetails(1L, "tester01", "hash");

		mockMvc.perform(get("/login").with(user(member))).andExpect(redirectedUrl("/products"));
		mockMvc.perform(get("/signup").with(user(member))).andExpect(redirectedUrl("/products"));
	}

	@Test
	void 로그인한_사용자의_가입_요청은_서비스를_호출하지_않고_상품_탐색으로_이동한다() throws Exception {
		MemberUserDetails member = new MemberUserDetails(1L, "tester01", "hash");

		mockMvc.perform(signup("other01", "password1", "password1").with(user(member)))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/products"));

		then(memberService).should(never()).signup(any(), any());
	}

	private static MockHttpServletRequestBuilder signup(String username, String password, String passwordConfirm) {
		return post("/signup").with(csrf())
				.param("username", username).param("password", password).param("passwordConfirm", passwordConfirm);
	}
}
