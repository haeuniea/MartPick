package com.martpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;

/**
 * 장바구니에서 회원가입으로 이동한 뒤 로그인하면 원래 화면으로 돌아오는 전체 흐름을 실제 보안 필터와 PostgreSQL로 확인한다.
 * 회원가입 성공 후 자동 로그인이나 비회원 장바구니 병합이 일어나지 않아야 하고, 변조된 {@code continue}는 가입 경로에서도 버려져야 한다.
 * 테스트 데이터는 트랜잭션 롤백으로 남기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SignupContinueFlowTest {

	private static final String PASSWORD = "password-Flow-1234";
	private static final long CUP_ID = 9_000_701L;
	private static final String CUP_NAME = "마트픽가입흐름 컵라면(110g)";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository productRepository;

	@BeforeEach
	void setUp() {
		productRepository.save(new Product(CUP_ID, CUP_NAME, "110", "G"));
		productRepository.flush();
	}

	@Test
	void 장바구니에서_가입으로_이동해_로그인하면_원래_장바구니로_돌아오고_자동_로그인과_병합은_없다() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/cart/items").session(session).with(csrf()).param("goodId", String.valueOf(CUP_ID)));

		String cartPage = mockMvc.perform(get("/cart").session(session)).andReturn().getResponse().getContentAsString();
		assertThat(cartPage).contains("href=\"/signup?continue=/cart\"");

		String signupPage = mockMvc.perform(get("/signup").session(session).param("continue", "/cart"))
				.andReturn().getResponse().getContentAsString();
		assertThat(hiddenContinue(signupPage)).isEqualTo("/cart");

		String redirect = mockMvc.perform(signupRequest("flowUser01", "/cart").session(session))
				.andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
		assertThat(continueOf(redirect)).isEqualTo("/cart");
		assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
				.as("가입만으로는 로그인되지 않는다").isNull();

		String loginPage = mockMvc.perform(get("/login").session(session).param("continue", continueOf(redirect)))
				.andReturn().getResponse().getContentAsString();
		assertThat(hiddenContinue(loginPage)).isEqualTo("/cart");

		mockMvc.perform(post("/login").session(session).with(csrf())
						.param("username", "flowUser01").param("password", PASSWORD).param("continue", hiddenContinue(loginPage)))
				.andExpect(redirectedUrl("/cart"));

		String memberCart = mockMvc.perform(get("/cart").session(session)).andReturn().getResponse().getContentAsString();
		assertThat(memberCart).contains("장바구니가 비어 있습니다.").doesNotContain(CUP_NAME);
	}

	@Test
	void 특수문자가_있는_복귀_주소도_가입_경로를_거치며_변형되거나_이중_인코딩되지_않는다() throws Exception {
		String keyword = "A&B #1+1 100% 우유";
		String target = "/products?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&page=2";

		String signupPage = mockMvc.perform(get("/signup").param("continue", target))
				.andReturn().getResponse().getContentAsString();
		assertThat(hiddenContinue(signupPage)).isEqualTo(target);

		String redirect = mockMvc.perform(signupRequest("flowUser02", hiddenContinue(signupPage)))
				.andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
		assertThat(continueOf(redirect)).isEqualTo(target);

		String loginPage = mockMvc.perform(get("/login").param("continue", continueOf(redirect)))
				.andReturn().getResponse().getContentAsString();
		assertThat(hiddenContinue(loginPage)).isEqualTo(target);

		String landed = mockMvc.perform(post("/login").with(csrf())
						.param("username", "flowUser02").param("password", PASSWORD).param("continue", hiddenContinue(loginPage)))
				.andReturn().getResponse().getRedirectedUrl();
		assertThat(landed).isEqualTo(target);
		assertThat(URLDecoder.decode(
				UriComponentsBuilder.fromUriString(landed).build().getQueryParams().getFirst("keyword"), StandardCharsets.UTF_8))
				.isEqualTo(keyword);
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://evil.com", "//evil.com", "/\\evil.com", "/admin", "/logout", "/login", "/signup",
			"javascript:alert(1)", "%2Fcart", "%252Fcart", "/cart/../admin", "/cart\r\nSet-Cookie: x=1",
			"/cart%0d%0aLocation:https://evil.com", "/products?keyword=a%00b", "http://localhost/cart", " /cart", "cart"})
	void 변조되거나_허용되지_않은_복귀_주소는_가입_화면과_가입_후_이동에서_모두_버린다(String tampered) throws Exception {
		String signupPage = mockMvc.perform(get("/signup").param("continue", tampered))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(signupPage).doesNotContain("name=\"continue\"");
		assertThat(signupPage).doesNotContain("evil.com");

		mockMvc.perform(signupRequest("flowTamper" + Math.abs(tampered.hashCode() % 100000), tampered))
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void 허용_경로의_허용되지_않은_쿼리는_가입_경로에서도_걸러_낸다() throws Exception {
		String redirect = mockMvc.perform(signupRequest("flowQuery01", "/compare?areaCode=020100000&next=https://evil.com&x=1"))
				.andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();

		assertThat(continueOf(redirect)).isEqualTo("/compare?areaCode=020100000");
		assertThat(redirect).doesNotContain("evil.com");
	}

	@Test
	void 가입_입력이_잘못되어_다시_보여_줄_때도_유효한_복귀_주소를_유지한다() throws Exception {
		String html = mockMvc.perform(post("/signup").with(csrf())
						.param("username", "ab").param("password", "short").param("passwordConfirm", "other")
						.param("continue", "/cart"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(hiddenContinue(html)).isEqualTo("/cart");
		assertThat(html).contains("href=\"/login?continue=/cart\"");
	}

	private MockHttpServletRequestBuilder signupRequest(String username, String continueUrl) {
		return post("/signup").with(csrf())
				.param("username", username).param("password", PASSWORD).param("passwordConfirm", PASSWORD)
				.param("continue", continueUrl);
	}

	private static String continueOf(String redirectUrl) {
		assertThat(redirectUrl).as("가입 후 이동 주소").startsWith("/login?");
		String raw = UriComponentsBuilder.fromUriString(redirectUrl).build().getQueryParams().getFirst("continue");
		assertThat(raw).as("가입 후 로그인 주소의 continue").isNotNull();
		return URLDecoder.decode(raw, StandardCharsets.UTF_8);
	}

	private static String hiddenContinue(String html) {
		Matcher matcher = Pattern.compile("name=\"continue\" value=\"([^\"]*)\"").matcher(html);
		assertThat(matcher.find()).as("숨은 continue 입력").isTrue();
		return matcher.group(1).replace("&amp;", "&");
	}
}
