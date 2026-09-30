package com.martpick.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

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
	void 로그인_없이_상품_목록과_기준_조사일_가격_누락_안내를_표시한다() throws Exception {
		given(productService.search(null, 1)).willReturn(onePage(
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
				.andExpect(content().string(containsString("비교 결과에서 가격 정보 없음으로 표시될 수 있습니다")))
				.andExpect(content().string(not(containsString("수집된 일부 상품"))))
				.andExpect(content().string(not(containsString("검색 결과가 없습니다"))));
	}

	@Test
	void 페이지_번호를_해석해_서비스에_넘긴다() throws Exception {
		Map<String, Integer> cases = Map.of("3", 3, "0", 1, "-2", 1, "abc", 1, "", 1, "1.5", 1,
				"99999999999", Integer.MAX_VALUE);
		for (Map.Entry<String, Integer> entry : cases.entrySet()) {
			given(productService.search(eq("왕뚜껑"), anyInt())).willReturn(onePage());

			mockMvc.perform(get("/products").param("keyword", "왕뚜껑").param("page", entry.getKey()))
					.andExpect(status().isOk());

			then(productService).should().search("왕뚜껑", entry.getValue());
			clearInvocations(productService);
		}
	}

	@Test
	void 페이지_번호가_없으면_첫_페이지를_요청한다() throws Exception {
		given(productService.search(null, 1)).willReturn(onePage());

		mockMvc.perform(get("/products")).andExpect(status().isOk());

		then(productService).should().search(null, 1);
	}

	@Test
	void 검색어와_페이지를_유지하는_페이지_이동_링크와_현재_페이지를_표시한다() throws Exception {
		given(productService.search("우유", 2)).willReturn(new ProductPage(
				List.of(new ProductView(224L, "서울우유 흰우유(1L)", "1000 ML")), 2, 3, 61));

		mockMvc.perform(get("/products").param("keyword", "우유").param("page", "2"))
				.andExpect(model().attribute("result", new ProductPage(
						List.of(new ProductView(224L, "서울우유 흰우유(1L)", "1000 ML")), 2, 3, 61)))
				.andExpect(content().string(containsString("총 <strong>61</strong>개")))
				.andExpect(content().string(containsString("href=\"/products?keyword=%EC%9A%B0%EC%9C%A0&amp;page=1\"")))
				.andExpect(content().string(containsString("href=\"/products?keyword=%EC%9A%B0%EC%9C%A0&amp;page=3\"")))
				.andExpect(content().string(containsString("aria-current=\"page\"")))
				.andExpect(content().string(containsString("이전")))
				.andExpect(content().string(containsString("다음")));
	}

	@Test
	void 특수문자와_한글이_포함된_검색어도_페이지_링크에서_그대로_복원된다() throws Exception {
		for (String keyword : new String[] {"A&B", "1+1", "#햇반", "우유 100%", "a=b?c/d", "라면 & 김치+1#"}) {
			given(productService.search(keyword, 1)).willReturn(new ProductPage(
					List.of(new ProductView(224L, "서울우유 흰우유(1L)", null)), 1, 2, 31));

			String html = mockMvc.perform(get("/products").param("keyword", keyword))
					.andReturn().getResponse().getContentAsString();

			Matcher next = Pattern.compile("href=\"(/products\\?[^\"]*page=2)\"").matcher(html);
			assertThat(next.find()).as(keyword).isTrue();
			String href = next.group(1).replace("&amp;", "&");
			MultiValueMap<String, String> query = UriComponentsBuilder.fromUriString(href).build(true).getQueryParams();
			assertThat(URLDecoder.decode(query.getFirst("keyword"), StandardCharsets.UTF_8)).as(keyword).isEqualTo(keyword);
			assertThat(query.get("page")).as(keyword).containsExactly("2");
		}
	}

	@Test
	void 검색어가_없으면_페이지_링크에_검색어를_붙이지_않는다() throws Exception {
		given(productService.search(null, 1)).willReturn(new ProductPage(
				List.of(new ProductView(224L, "서울우유 흰우유(1L)", "1000 ML")), 1, 2, 31));

		mockMvc.perform(get("/products"))
				.andExpect(content().string(containsString("href=\"/products?page=2\"")))
				.andExpect(content().string(not(containsString("keyword=&amp;"))));
	}

	@Test
	void 한_페이지뿐이면_페이지_이동을_표시하지_않는다() throws Exception {
		given(productService.search(null, 1)).willReturn(onePage(new ProductView(224L, "서울우유 흰우유(1L)", null)));

		mockMvc.perform(get("/products"))
				.andExpect(content().string(not(containsString("class=\"pagination\""))));
	}

	@Test
	void 검색어를_앞뒤_공백_없이_다시_표시한다() throws Exception {
		given(productService.search("  왕뚜껑 ", 1)).willReturn(onePage(new ProductView(1000L, "팔도 왕뚜껑(110g)", null)));

		mockMvc.perform(get("/products").param("keyword", "  왕뚜껑 "))
				.andExpect(status().isOk())
				.andExpect(model().attribute("keyword", "왕뚜껑"))
				.andExpect(content().string(containsString("value=\"왕뚜껑\"")));
	}

	@Test
	void 검색_결과가_없으면_안내한다() throws Exception {
		given(productService.search("없는상품", 1)).willReturn(onePage());

		mockMvc.perform(get("/products").param("keyword", "없는상품"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("검색 결과가 없습니다")))
				.andExpect(content().string(not(containsString("class=\"pagination\""))));
	}

	@Test
	void 상품마다_CSRF_토큰과_검색어_페이지를_포함한_담기_폼과_장바구니_보기_링크를_표시한다() throws Exception {
		given(productService.search("왕뚜껑", 2)).willReturn(new ProductPage(
				List.of(new ProductView(1000L, "팔도 왕뚜껑(110g)", "110 G")), 2, 2, 31));

		mockMvc.perform(get("/products").param("keyword", "왕뚜껑").param("page", "2"))
				.andExpect(content().string(containsString("action=\"/cart/items\"")))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("name=\"goodId\" value=\"1000\"")))
				.andExpect(content().string(containsString("name=\"keyword\" value=\"왕뚜껑\"")))
				.andExpect(content().string(containsString("name=\"page\" value=\"2\"")))
				.andExpect(content().string(containsString("href=\"/cart\"")))
				.andExpect(content().string(containsString("장바구니 보기")));
	}

	@Test
	void 담기_결과_안내를_표시한다() throws Exception {
		given(productService.search(null, 1)).willReturn(onePage());

		mockMvc.perform(get("/products").flashAttr("message", "햇반(210g)을(를) 장바구니에 담았습니다."))
				.andExpect(content().string(containsString("햇반(210g)을(를) 장바구니에 담았습니다.")));
		mockMvc.perform(get("/products").flashAttr("error", "상품당 최대 99개까지 담을 수 있습니다."))
				.andExpect(content().string(containsString("상품당 최대 99개까지 담을 수 있습니다.")));
	}

	@Test
	void 검색어의_HTML은_이스케이프한다() throws Exception {
		given(productService.search("<script>alert(1)</script>", 1)).willReturn(onePage());

		mockMvc.perform(get("/products").param("keyword", "<script>alert(1)</script>"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(containsString("&lt;script&gt;")));
	}

	private static ProductPage onePage(ProductView... products) {
		return new ProductPage(List.of(products), 1, products.length == 0 ? 0 : 1, products.length);
	}
}
