package com.martpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
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

	private static MockHttpServletRequestBuilder signup(String username, String password, String passwordConfirm) {
		return post("/signup").with(csrf())
				.param("username", username).param("password", password).param("passwordConfirm", passwordConfirm);
	}
}
