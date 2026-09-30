package com.martpick.cart;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.martpick.compare.ComparisonService;
import com.martpick.compare.RegionOption;
import com.martpick.config.SecurityConfig;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
class CartControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GuestCartService guestCartService;

	@MockitoBean
	private ComparisonService comparisonService;

	@Test
	void 담기에_성공하면_검색어를_유지한_상품_탐색_화면으로_돌아가_안내한다() throws Exception {
		given(guestCartService.add(1000L)).willReturn("팔도 왕뚜껑(110g)");

		mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "1000").param("keyword", "왕뚜껑"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/products?keyword=%EC%99%95%EB%9A%9C%EA%BB%91"))
				.andExpect(flash().attribute("message", "팔도 왕뚜껑(110g)을(를) 장바구니에 담았습니다."));
	}

	@Test
	void 검색어가_없으면_전체_상품_탐색_화면으로_돌아간다() throws Exception {
		given(guestCartService.add(1000L)).willReturn("팔도 왕뚜껑(110g)");

		mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "1000").param("keyword", " "))
				.andExpect(redirectedUrl("/products"));
	}

	@Test
	void 담기를_거부하면_상품_탐색_화면에서_사유를_안내한다() throws Exception {
		given(guestCartService.add(1000L)).willThrow(new InvalidCartRequestException("상품당 최대 99개까지 담을 수 있습니다."));

		mockMvc.perform(post("/cart/items").with(csrf()).param("goodId", "1000"))
				.andExpect(redirectedUrl("/products"))
				.andExpect(flash().attribute("error", "상품당 최대 99개까지 담을 수 있습니다."));
	}

	@Test
	void CSRF_토큰_없는_장바구니_변경은_거부한다() throws Exception {
		mockMvc.perform(post("/cart/items").param("goodId", "1000")).andExpect(status().isForbidden());
		mockMvc.perform(post("/cart/items/1000/quantity").param("quantity", "3")).andExpect(status().isForbidden());
		mockMvc.perform(post("/cart/items/1000/delete")).andExpect(status().isForbidden());

		then(guestCartService).should(never()).add(anyLong());
		then(guestCartService).should(never()).changeQuantity(anyLong(), anyInt());
		then(guestCartService).should(never()).remove(anyLong());
	}

	@Test
	void 장바구니_화면에_상품과_비교_가능_지역을_가격_없이_표시한다() throws Exception {
		given(guestCartService.items()).willReturn(List.of(
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
		given(guestCartService.items()).willReturn(List.of());

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

		then(guestCartService).should().changeQuantity(1000L, 5);
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

		then(guestCartService).should(never()).changeQuantity(anyLong(), anyInt());
	}

	@Test
	void 서비스가_수량을_거부하면_오류를_안내한다() throws Exception {
		willThrow(new InvalidCartRequestException("수량은 1~99 사이의 정수로 입력해 주세요."))
				.given(guestCartService).changeQuantity(1000L, 5);

		mockMvc.perform(post("/cart/items/1000/quantity").with(csrf()).param("quantity", "5"))
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attribute("error", "수량은 1~99 사이의 정수로 입력해 주세요."));
	}

	@Test
	void 상품을_삭제하면_장바구니_화면으로_돌아간다() throws Exception {
		mockMvc.perform(post("/cart/items/1000/delete").with(csrf()))
				.andExpect(redirectedUrl("/cart"));

		then(guestCartService).should().remove(1000L);
	}
}
