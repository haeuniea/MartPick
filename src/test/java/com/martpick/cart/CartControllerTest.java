package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.martpick.compare.ComparisonService;
import com.martpick.compare.RegionOption;
import com.martpick.config.SecurityConfig;
import com.martpick.member.MemberUserDetails;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
class CartControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CartService cartService;

	@MockitoBean
	private ComparisonService comparisonService;

	@Test
	void 담기에_성공하면_검색어를_유지한_상품_탐색_화면으로_돌아가_안내한다() throws Exception {
		given(cartService.add(null, 1000L)).willReturn("팔도 왕뚜껑(110g)");

		mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "1000").param("keyword", "왕뚜껑"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/products?keyword=%EC%99%95%EB%9A%9C%EA%BB%91#product-1000"))
				.andExpect(flash().attribute("message", "팔도 왕뚜껑(110g)을(를) 장바구니에 담았습니다."));
	}

	@Test
	void 담기_후_담은_상품_위치로_돌아가도록_상품을_표시한다() throws Exception {
		given(cartService.add(null, 1000L)).willReturn("팔도 왕뚜껑(110g)");
		given(cartService.add(null, 292L)).willThrow(new InvalidCartRequestException("상품당 최대 99개까지 담을 수 있습니다."));

		mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "1000"))
				.andExpect(redirectedUrl("/products#product-1000"))
				.andExpect(flash().attribute("lastGoodId", 1000L));
		mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "292"))
				.andExpect(redirectedUrl("/products#product-292"))
				.andExpect(flash().attribute("lastGoodId", 292L));
	}

	@Test
	void 담기_후_검색어와_페이지_번호를_유지한다() throws Exception {
		given(cartService.add(null, 1000L)).willReturn("팔도 왕뚜껑(110g)");

		mockMvc.perform(post("/cart/items").with(csrf())
						.param("goodId", "1000").param("keyword", "왕뚜껑").param("page", "3"))
				.andExpect(redirectedUrl("/products?keyword=%EC%99%95%EB%9A%9C%EA%BB%91&page=3#product-1000"));
		mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "1000").param("page", "2"))
				.andExpect(redirectedUrl("/products?page=2#product-1000"));
	}

	@Test
	void 특수문자와_한글이_포함된_검색어도_담기_후_그대로_복원된다() throws Exception {
		given(cartService.add(null, 1000L)).willReturn("팔도 왕뚜껑(110g)");

		for (String keyword : new String[] {"A&B", "1+1", "#햇반", "우유 100%", "a=b?c/d", "라면 & 김치+1#"}) {
			String redirectedUrl = mockMvc.perform(post("/cart/items").with(csrf())
							.param("goodId", "1000").param("keyword", keyword).param("page", "2"))
					.andExpect(status().is3xxRedirection())
					.andReturn().getResponse().getRedirectedUrl();

			MultiValueMap<String, String> query = UriComponentsBuilder.fromUriString(redirectedUrl).build(true).getQueryParams();
			assertThat(URLDecoder.decode(query.getFirst("keyword"), StandardCharsets.UTF_8)).as(keyword).isEqualTo(keyword);
			assertThat(query.get("page")).as(keyword).containsExactly("2");
			assertThat(redirectedUrl).as(keyword).endsWith("#product-1000");
			assertThat(redirectedUrl.substring(0, redirectedUrl.length() - "#product-1000".length())).as(keyword).doesNotContain("#");
		}
	}

	@Test
	void 담기를_거부해도_검색어와_페이지_번호를_유지한다() throws Exception {
		given(cartService.add(null, 1000L)).willThrow(new InvalidCartRequestException("상품당 최대 99개까지 담을 수 있습니다."));

		mockMvc.perform(post("/cart/items").with(csrf())
						.param("goodId", "1000").param("keyword", "왕뚜껑").param("page", "3"))
				.andExpect(redirectedUrl("/products?keyword=%EC%99%95%EB%9A%9C%EA%BB%91&page=3#product-1000"))
				.andExpect(flash().attribute("error", "상품당 최대 99개까지 담을 수 있습니다."));
	}

	@Test
	void 잘못된_페이지_번호는_담기_후_이동_주소에_붙이지_않는다() throws Exception {
		given(cartService.add(null, 1000L)).willReturn("팔도 왕뚜껑(110g)");

		for (String page : new String[] {"abc", "0", "-1", "", "1.5"}) {
			mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "1000").param("page", page))
					.andExpect(redirectedUrl("/products#product-1000"));
		}
	}

	@Test
	void 검색어가_없으면_전체_상품_탐색_화면으로_돌아간다() throws Exception {
		given(cartService.add(null, 1000L)).willReturn("팔도 왕뚜껑(110g)");

		mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "1000").param("keyword", " "))
				.andExpect(redirectedUrl("/products#product-1000"));
	}

	@Test
	void 담기를_거부하면_상품_탐색_화면에서_사유를_안내한다() throws Exception {
		given(cartService.add(null, 1000L)).willThrow(new InvalidCartRequestException("상품당 최대 99개까지 담을 수 있습니다."));

		mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "1000"))
				.andExpect(redirectedUrl("/products#product-1000"))
				.andExpect(flash().attribute("error", "상품당 최대 99개까지 담을 수 있습니다."));
	}

	@Test
	void CSRF_토큰_없는_장바구니_변경은_거부한다() throws Exception {
		mockMvc.perform(post("/cart/items").param("goodId", "1000")).andExpect(status().isForbidden());
		mockMvc.perform(post("/cart/items/1000/quantity").param("quantity", "3")).andExpect(status().isForbidden());
		mockMvc.perform(post("/cart/items/1000/delete")).andExpect(status().isForbidden());

		then(cartService).should(never()).add(any(), anyLong());
		then(cartService).should(never()).changeQuantity(any(), anyLong(), anyInt());
		then(cartService).should(never()).remove(any(), anyLong());
	}

	@Test
	void 장바구니_화면에_상품과_비교_가능_지역을_가격_없이_표시한다() throws Exception {
		given(cartService.items(null)).willReturn(List.of(
				new CartItemView(993L, "오리온 초코파이(18개입)", "702 G", 3)));
		given(comparisonService.comparableRegions()).willReturn(List.of(
				new RegionOption("020100000", "서울특별시"), new RegionOption("020200000", "부산광역시")));

		mockMvc.perform(get("/cart"))
				.andExpect(status().isOk())
				.andExpect(view().name("cart"))
				.andExpect(content().string(containsString("오리온 초코파이(18개입)")))
				.andExpect(content().string(containsString("702 G")))
				.andExpect(content().string(containsString("value=\"3\"")))
				.andExpect(content().string(containsString("<option value=\"020100000\">서울특별시</option>")))
				.andExpect(content().string(containsString("<option value=\"020200000\">부산광역시</option>")))
				.andExpect(content().string(containsString("2026년 9월 25일")))
				.andExpect(content().string(not(containsString("원</"))));
	}

	@Test
	void 빈_장바구니는_안내만_표시하고_지역_선택을_표시하지_않는다() throws Exception {
		given(cartService.items(null)).willReturn(List.of());

		mockMvc.perform(get("/cart"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("장바구니가 비어 있습니다")))
				.andExpect(content().string(not(containsString("name=\"areaCode\""))));
		then(comparisonService).should(never()).comparableRegions();
	}

	@Test
	void 수량을_변경하면_장바구니_화면으로_돌아간다() throws Exception {
		mockMvc.perform(post("/cart/items/1000/quantity").with(csrf()).param("quantity", "5"))
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attributeCount(0));

		then(cartService).should().changeQuantity(null, 1000L, 5);
	}

	@Test
	void 잘못된_수량은_변경하지_않고_오류를_안내한다() throws Exception {
		for (String quantity : new String[] {"0", "-1", "100", "1.5", "abc", ""}) {
			mockMvc.perform(post("/cart/items/1000/quantity").with(csrf()).param("quantity", quantity))
					.andExpect(redirectedUrl("/cart"))
					.andExpect(flash().attribute("error", "수량은 1~99 사이의 정수로 입력해 주세요."));
		}
		mockMvc.perform(post("/cart/items/1000/quantity").with(csrf()))
				.andExpect(flash().attribute("error", "수량은 1~99 사이의 정수로 입력해 주세요."));

		then(cartService).should(never()).changeQuantity(any(), anyLong(), anyInt());
	}

	@Test
	void 서비스가_수량을_거부하면_오류를_안내한다() throws Exception {
		willThrow(new InvalidCartRequestException("수량은 1~99 사이의 정수로 입력해 주세요."))
				.given(cartService).changeQuantity(null, 1000L, 5);

		mockMvc.perform(post("/cart/items/1000/quantity").with(csrf()).param("quantity", "5"))
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attribute("error", "수량은 1~99 사이의 정수로 입력해 주세요."));
	}

	@Test
	void 상품을_삭제하면_장바구니_화면으로_돌아간다() throws Exception {
		mockMvc.perform(post("/cart/items/1000/delete").with(csrf()))
				.andExpect(redirectedUrl("/cart"));

		then(cartService).should().remove(null, 1000L);
	}

	@Test
	void 비회원의_장바구니에_상품이_있으면_자동_병합되지_않는다는_안내와_로그인_가입_링크를_보여_준다() throws Exception {
		given(cartService.items(null)).willReturn(List.of(new CartItemView(993L, "오리온 초코파이(18개입)", "702 G", 3)));
		given(comparisonService.comparableRegions()).willReturn(List.of(new RegionOption("020100000", "서울특별시")));

		String html = mockMvc.perform(get("/cart")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String notice = guestNotice(html);
		assertThat(notice).contains("로그인하면 지금 담은 상품은 회원 장바구니로 옮겨지지 않습니다. 로그인한 뒤 다시 담아 주세요.");
		assertThat(notice).contains("href=\"/login?continue=/cart\"", "href=\"/signup?continue=/cart\"");
	}

	@Test
	void 비회원이라도_장바구니가_비어_있으면_병합_안내를_보여_주지_않는다() throws Exception {
		given(cartService.items(null)).willReturn(List.of());

		mockMvc.perform(get("/cart")).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("cart-guest-notice"))))
				.andExpect(content().string(not(containsString("회원 장바구니로 옮겨지지 않습니다"))));
	}

	@Test
	void 로그인한_회원에게는_비회원_병합_안내를_보여_주지_않는다() throws Exception {
		MemberUserDetails member = new MemberUserDetails(7L, "tester01", "hash");
		given(cartService.items(member)).willReturn(List.of(new CartItemView(993L, "오리온 초코파이(18개입)", "702 G", 3)));
		given(comparisonService.comparableRegions()).willReturn(List.of(new RegionOption("020100000", "서울특별시")));

		mockMvc.perform(get("/cart").with(user(member))).andExpect(status().isOk())
				.andExpect(content().string(containsString("오리온 초코파이(18개입)")))
				.andExpect(content().string(not(containsString("cart-guest-notice"))))
				.andExpect(content().string(not(containsString("회원 장바구니로 옮겨지지 않습니다"))));
	}

	private static String guestNotice(String html) {
		int start = html.indexOf("cart-guest-notice");
		assertThat(start).as("비회원 병합 안내 영역").isNotNegative();
		return html.substring(start, html.indexOf("</div>", start));
	}

	@Test
	void 로그인한_회원의_담기에는_인증된_회원_주체가_전달된다() throws Exception {
		MemberUserDetails member = new MemberUserDetails(7L, "tester01", "hash");
		given(cartService.add(member, 1000L)).willReturn("팔도 왕뚜껑(110g)");

		mockMvc.perform(post("/cart/items").with(user(member)).with(csrf()).param("goodId", "1000"))
				.andExpect(flash().attribute("message", "팔도 왕뚜껑(110g)을(를) 장바구니에 담았습니다."));

		then(cartService).should().add(member, 1000L);
	}

	@Test
	void 로그인한_회원의_조회_수량_변경_삭제에도_인증된_회원_주체가_전달된다() throws Exception {
		MemberUserDetails member = new MemberUserDetails(7L, "tester01", "hash");
		given(cartService.items(member)).willReturn(List.of(new CartItemView(993L, "오리온 초코파이(18개입)", "702 G", 3)));
		given(comparisonService.comparableRegions()).willReturn(List.of(new RegionOption("020100000", "서울특별시")));

		mockMvc.perform(get("/cart").with(user(member)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("오리온 초코파이(18개입)")));
		mockMvc.perform(post("/cart/items/1000/quantity").with(user(member)).with(csrf()).param("quantity", "5"))
				.andExpect(redirectedUrl("/cart"));
		mockMvc.perform(post("/cart/items/1000/delete").with(user(member)).with(csrf()))
				.andExpect(redirectedUrl("/cart"));

		then(cartService).should().items(member);
		then(cartService).should().changeQuantity(member, 1000L, 5);
		then(cartService).should().remove(member, 1000L);
	}

	@Test
	void 요청_파라미터의_회원_ID는_사용하지_않는다() throws Exception {
		MemberUserDetails member = new MemberUserDetails(7L, "tester01", "hash");
		given(cartService.add(member, 1000L)).willReturn("팔도 왕뚜껑(110g)");

		mockMvc.perform(post("/cart/items").with(user(member)).with(csrf())
				.param("goodId", "1000").param("memberId", "999").param("member", "999").param("id", "999"));
		mockMvc.perform(post("/cart/items/1000/quantity").with(user(member)).with(csrf())
				.param("quantity", "5").param("memberId", "999"));
		mockMvc.perform(post("/cart/items/1000/delete").with(user(member)).with(csrf()).param("memberId", "999"));

		then(cartService).should().add(member, 1000L);
		then(cartService).should().changeQuantity(member, 1000L, 5);
		then(cartService).should().remove(member, 1000L);
		then(cartService).shouldHaveNoMoreInteractions();
	}
}
