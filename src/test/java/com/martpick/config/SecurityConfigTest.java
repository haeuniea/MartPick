package com.martpick.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.martpick.member.MemberUserDetailsService;

/**
 * 보안 설정을 실제 애플리케이션 컨텍스트로 확인한다. 5-2에서 로그인·로그아웃을 추가하며 인증 관련 단언을 조정했다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityConfigTest.InternalProbeController.class)
class SecurityConfigTest {

	/** 컨트롤러는 있지만 허용 목록에 없는 경로를 만들어, 허용 목록 밖은 여전히 거부되는지 확인하는 테스트 전용 컨트롤러 */
	@Controller
	static class InternalProbeController {

		@GetMapping("/internal-probe")
		@ResponseBody
		String read() {
			return "internal-secret";
		}

		@PostMapping("/internal-probe")
		@ResponseBody
		String write() {
			return "internal-secret";
		}
	}

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
		MvcResult result = mockMvc.perform(get("/admin")).andExpect(status().isNotFound()).andReturn();

		assertThat(result.getResponse().getRedirectedUrl()).isNull();
		assertThat(result.getRequest().getSession(false)).isNull();
		mockMvc.perform(get("/cart/items")).andExpect(status().isMethodNotAllowed());
		mockMvc.perform(get("/logout")).andExpect(status().isNotFound());
	}

	@Test
	void 컨트롤러가_없는_허용_목록_밖_경로는_404이고_로그인한_사용자도_같다() throws Exception {
		for (String path : new String[] {"/admin", "/actuator/health", "/products/", "/logout"}) {
			mockMvc.perform(get(path)).andExpect(status().isNotFound());
			mockMvc.perform(get(path).with(user("tester01"))).andExpect(status().isNotFound());
		}
	}

	@Test
	void 컨트롤러가_있어도_허용_목록에_없으면_여전히_403으로_거부하고_내용을_돌려주지_않는다() throws Exception {
		for (boolean signedIn : new boolean[] {false, true}) {
			var read = get("/internal-probe");
			var write = post("/internal-probe").with(csrf());
			if (signedIn) {
				read = read.with(user("tester01"));
				write = write.with(user("tester01"));
			}
			mockMvc.perform(read).andExpect(status().isForbidden())
					.andExpect(content().string(not(containsString("internal-secret"))));
			mockMvc.perform(write).andExpect(status().isForbidden())
					.andExpect(content().string(not(containsString("internal-secret"))));
		}
	}

	@Test
	void 허용_목록_밖_경로에서_CSRF_토큰이_없는_요청은_경로와_관계없이_403이다() throws Exception {
		mockMvc.perform(post("/internal-probe")).andExpect(status().isForbidden());
		mockMvc.perform(post("/nope")).andExpect(status().isForbidden());
		mockMvc.perform(post("/nope").with(user("tester01"))).andExpect(status().isForbidden());
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
