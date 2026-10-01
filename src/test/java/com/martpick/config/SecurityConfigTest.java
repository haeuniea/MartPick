package com.martpick.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.martpick.member.MemberUserDetailsService;

/**
 * 보안 설정을 실제 애플리케이션 컨텍스트로 확인한다. 5-2에서 로그인·로그아웃을 추가하며 인증 관련 단언을 조정했다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@Test
	void 비회원은_로그인_없이_상품_탐색_장바구니_비교_화면과_CSS를_사용한다() throws Exception {
		mockMvc.perform(get("/products")).andExpect(status().isOk());
		mockMvc.perform(get("/cart")).andExpect(status().isOk());
		mockMvc.perform(get("/compare")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/css/main.css")).andExpect(status().isOk());
	}

	@Test
	void 비회원은_로그인_없이_회원가입_화면을_사용한다() throws Exception {
		mockMvc.perform(get("/signup")).andExpect(status().isOk());
	}

	@Test
	void 로그인_화면은_Spring_기본_화면이_아니라_마트픽_화면이다() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("마트픽")))
				.andExpect(content().string(not(containsString("Please sign in"))));
	}

	@Test
	void UserDetailsService는_회원_저장소_기반_하나뿐이고_기본_사용자는_없다() throws Exception {
		assertThat(context.getBeansOfType(UserDetailsService.class).values())
				.singleElement().isInstanceOf(MemberUserDetailsService.class);

		for (String password : new String[] {"password", ""}) {
			mockMvc.perform(post("/login").with(csrf()).param("username", "user").param("password", password))
					.andExpect(redirectedUrl("/login?error"));
		}
	}

	@Test
	void 허용하지_않은_경로는_로그인_화면으로_보내지_않고_거부하며_세션도_만들지_않는다() throws Exception {
		MvcResult result = mockMvc.perform(get("/admin")).andExpect(status().isForbidden()).andReturn();

		assertThat(result.getResponse().getRedirectedUrl()).isNull();
		assertThat(result.getRequest().getSession(false)).isNull();
		mockMvc.perform(get("/cart/items")).andExpect(status().isMethodNotAllowed());
		mockMvc.perform(get("/logout")).andExpect(status().isForbidden());
	}

	@Test
	void CSRF_토큰_없는_POST는_거부한다() throws Exception {
		mockMvc.perform(post("/products")).andExpect(status().isForbidden());
		mockMvc.perform(post("/products").with(csrf())).andExpect(status().isMethodNotAllowed());
		for (String path : new String[] {"/login", "/logout", "/signup", "/cart/items"}) {
			mockMvc.perform(post(path)).andExpect(status().isForbidden());
		}
	}
}
