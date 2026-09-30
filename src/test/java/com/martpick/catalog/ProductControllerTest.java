package com.martpick.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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

import com.martpick.config.SecurityConfig;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductService productService;

	@Test
	void 첫_화면은_상품_탐색_화면으로_이동한다() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/products"));
	}

	@Test
	void 로그인_없이_상품_목록과_기준_조사일_안내를_표시한다() throws Exception {
		given(productService.search(null)).willReturn(List.of(
				new ProductView(993L, "오리온 초코파이(18개입)", "702 G"),
				new ProductView(934L, "애호박", null)));

		mockMvc.perform(get("/products"))
				.andExpect(status().isOk())
				.andExpect(view().name("products"))
				.andExpect(content().string(containsString("오리온 초코파이(18개입)")))
				.andExpect(content().string(containsString("702 G")))
				.andExpect(content().string(containsString("애호박")))
				.andExpect(content().string(containsString("2026년 9월 25일")))
				.andExpect(content().string(containsString("실시간 판매 가격·재고를 보장하지 않습니다")))
				.andExpect(content().string(containsString("수집된 일부 상품")))
				.andExpect(content().string(not(containsString("검색 결과가 없습니다"))));
	}

	@Test
	void 검색어를_앞뒤_공백_없이_다시_표시한다() throws Exception {
		given(productService.search("  왕뚜껑 ")).willReturn(List.of(new ProductView(1000L, "팔도 왕뚜껑(110g)", null)));

		mockMvc.perform(get("/products").param("keyword", "  왕뚜껑 "))
				.andExpect(status().isOk())
				.andExpect(model().attribute("keyword", "왕뚜껑"))
				.andExpect(content().string(containsString("value=\"왕뚜껑\"")));
	}

	@Test
	void 검색_결과가_없으면_검색_범위_제한을_함께_안내한다() throws Exception {
		given(productService.search("없는상품")).willReturn(List.of());

		mockMvc.perform(get("/products").param("keyword", "없는상품"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("검색 결과가 없습니다")))
				.andExpect(content().string(containsString("수집된 일부 상품")));
	}

	@Test
	void 검색어의_HTML은_이스케이프한다() throws Exception {
		given(productService.search("<script>alert(1)</script>")).willReturn(List.of());

		mockMvc.perform(get("/products").param("keyword", "<script>alert(1)</script>"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(containsString("&lt;script&gt;")));
	}
}
