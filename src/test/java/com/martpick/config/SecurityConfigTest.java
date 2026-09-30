package com.martpick.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 4단계(비회원 기능) 보안 설정을 실제 애플리케이션 컨텍스트로 확인한다. 5단계 인증 구현 시 다시 조정한다.
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
	void 비회원은_로그인_없이_상품_탐색_화면과_CSS를_사용한다() throws Exception {
		mockMvc.perform(get("/products")).andExpect(status().isOk());
		mockMvc.perform(get("/css/main.css")).andExpect(status().isOk());
	}

	@Test
	void 기본_로그인_화면을_제공하지_않는다() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(not(containsString("Please sign in"))));
	}

	@Test
	void 자동_생성_사용자를_만들지_않는다() {
		assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
	}

	@Test
	void 허용하지_않은_경로는_거부한다() throws Exception {
		mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
	}

	@Test
	void CSRF_토큰_없는_POST는_거부한다() throws Exception {
		mockMvc.perform(post("/products")).andExpect(status().isForbidden());
		mockMvc.perform(post("/products").with(csrf())).andExpect(status().isMethodNotAllowed());
	}
}
