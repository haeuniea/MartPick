package com.martpick.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.martpick.cart.GuestCart;
import com.martpick.compare.InvalidComparisonRequestException.Reason;
import com.martpick.config.SecurityConfig;

@WebMvcTest(CompareController.class)
@Import(SecurityConfig.class)
class CompareControllerTest {

	private static final String SEOUL = "020100000";
	private static final LocalDate INSPECT_DAY = LocalDate.of(2026, 9, 25);
	private static final ProductInfo RAMEN = new ProductInfo(1000L, "팔도 왕뚜껑(110g)", "110", "G");
	private static final ProductInfo RICE = new ProductInfo(292L, "햇반(210g)", "210", "G");
	private static final ProductInfo MILK = new ProductInfo(224L, "서울우유 흰우유(1L)", "1000", "ML");
	private static final List<CartLine> CART = List.of(new CartLine(1000L, 2), new CartLine(292L, 1), new CartLine(224L, 1));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ComparisonService comparisonService;

	@MockitoBean
	private GuestCart guestCart;

	@BeforeEach
	void setUp() {
		given(guestCart.lines()).willReturn(CART);
	}

	@Test
	void 완전_비교_판매점의_상품별_단가_수량_소계와_장바구니_합계를_표시한다() throws Exception {
		givenResult(List.of(complete(101L, "가마트", "서울특별시 중구 세종대로 1", 1280, 1900, 2890)), List.of());

		String html = render();

		String complete = section(html, "complete-stores");
		assertThat(complete).contains("가마트", "서울특별시 중구 세종대로 1")
				.contains("팔도 왕뚜껑(110g)", "1,280원", "2,560원")
				.contains("햇반(210g)", "1,900원")
				.contains("서울우유 흰우유(1L)", "2,890원")
				.contains("장바구니 합계", "7,350원");
		assertThat(html).doesNotContain("id=\"partial-stores\"");
	}

	@Test
	void 상품명은_원문_그대로_규격이_있으면_따로_표시한다() throws Exception {
		ProductInfo noSpec = new ProductInfo(934L, "애호박", null, null);
		CompleteStore store = new CompleteStore(101L, "가마트", "주소",
				List.of(new ItemPrice(RAMEN, 1280, 1, 1280), new ItemPrice(noSpec, 1500, 1, 1500)), 2780);
		givenResult(List.of(store), List.of(partial(202L, "나마트", 2560)));

		String html = render();

		assertThat(section(html, "complete-stores")).contains("팔도 왕뚜껑(110g)", "110 G", "애호박")
				.doesNotContain("null");
		assertThat(section(html, "partial-stores")).contains("110 G")
				.contains("햇반(210g)", "210 G")
				.contains("서울우유 흰우유(1L)", "1000 ML");
	}

	@Test
	void 두_영역이_모두_있으면_각_영역_바로가기를_판매점_수와_함께_표시한다() throws Exception {
		givenResult(List.of(complete(101L, "가마트", "주소", 1000, 1000, 1000), complete(102L, "다마트", "주소", 1000, 1000, 1000)),
				List.of(partial(202L, "나마트", 2560)));

		String html = render();

		assertThat(html).contains("href=\"#complete-stores\"", "완전 비교 2곳")
				.contains("href=\"#partial-stores\"", "부분 비교 1곳");
	}

	@Test
	void 존재하는_영역의_바로가기만_표시한다() throws Exception {
		givenResult(List.of(), List.of(partial(202L, "나마트", 2560)));
		assertThat(render()).contains("href=\"#partial-stores\"").doesNotContain("href=\"#complete-stores\"");

		givenResult(List.of(complete(101L, "가마트", "주소", 1000, 1000, 1000)), List.of());
		assertThat(render()).contains("href=\"#complete-stores\"").doesNotContain("href=\"#partial-stores\"");

		givenResult(List.of(), List.of());
		assertThat(render()).doesNotContain("href=\"#complete-stores\"", "href=\"#partial-stores\"");
	}

	@Test
	void 조사_기준일과_실시간_가격이_아니라는_안내와_선택_지역을_표시한다() throws Exception {
		givenResult(List.of(complete(101L, "가마트", "주소", 1000, 1000, 1000)), List.of());

		assertThat(render()).contains("2026년 9월 25일", "실시간 판매 가격·재고를 보장하지 않습니다", "서울특별시");
	}

	@Test
	void 서비스가_정한_순서대로_표시하고_순위_숫자를_표시하지_않는다() throws Exception {
		givenResult(
				List.of(complete(300L, "다마트", "주소", 1000, 1000, 1000),
						complete(100L, "가마트", "주소", 1000, 1000, 1100)),
				List.of(partial(900L, "하마트", 9000), partial(200L, "나마트", 100)));

		String html = render();

		assertThat(html.indexOf("다마트")).isLessThan(html.indexOf("가마트"));
		assertThat(html.indexOf("하마트")).isLessThan(html.indexOf("나마트"));
		assertThat(html).doesNotContain("1위", "2위", "<ol");
	}

	@Test
	void 부분_비교_판매점은_확인된_상품_합계와_제외된_상품을_함께_표시한다() throws Exception {
		givenResult(List.of(complete(101L, "가마트", "주소", 1000, 1000, 1000)), List.of(partial(202L, "나마트", 2560)));

		String html = render();

		String partial = section(html, "partial-stores");
		assertThat(partial).contains("나마트", "서울특별시 중구 명동길 2")
				.contains("팔도 왕뚜껑(110g)", "1,280원", "2,560원")
				.contains("확인된 상품 합계 2,560원 (햇반(210g), 서울우유 흰우유(1L) 제외)")
				.contains("가격 정보 없음")
				.contains("장바구니 전체 가격이 아닙니다");
		assertThat(partial).doesNotContain("장바구니 합계", ">0원<", "품절");
	}

	@Test
	void 완전_비교_판매점이_없으면_사유를_안내하고_부분_비교_목록만_표시한다() throws Exception {
		givenResult(List.of(), List.of(partial(202L, "나마트", 2560)));

		String html = render();

		assertThat(html).contains("모든 상품의 가격이 확인된 판매점이 없습니다")
				.contains("id=\"partial-stores\"")
				.doesNotContain("id=\"complete-stores\"");
	}

	@Test
	void 가격이_확인된_판매점이_없으면_사유를_안내한다() throws Exception {
		givenResult(List.of(), List.of());

		String html = render();

		assertThat(html).contains("장바구니 상품의 가격이 확인된 판매점이 없습니다")
				.doesNotContain("id=\"complete-stores\"", "id=\"partial-stores\"");
	}

	@Test
	void 잘못된_비교_요청은_장바구니_화면으로_돌아가_사유를_안내한다() throws Exception {
		assertRedirectWithError(Reason.EMPTY_CART, "장바구니가 비어 있습니다. 비교할 상품을 담아 주세요.");
		assertRedirectWithError(Reason.REGION_REQUIRED, "비교할 지역을 선택해 주세요.");
		assertRedirectWithError(Reason.UNKNOWN_REGION, "비교할 수 없는 지역입니다. 목록에서 지역을 다시 선택해 주세요.");
		assertRedirectWithError(Reason.UNKNOWN_PRODUCT, "장바구니에 비교할 수 없는 상품이 있습니다. 장바구니를 확인해 주세요.");
		for (Reason reason : List.of(Reason.INVALID_CART_LINE, Reason.QUANTITY_OUT_OF_RANGE, Reason.DUPLICATE_PRODUCT)) {
			assertRedirectWithError(reason, "장바구니 정보가 올바르지 않습니다. 장바구니를 확인해 주세요.");
		}
	}

	@Test
	void 지역을_선택하지_않으면_빈_지역으로_서비스에_요청한다() throws Exception {
		given(comparisonService.compare(null, CART))
				.willThrow(new InvalidComparisonRequestException(Reason.REGION_REQUIRED, "비교할 지역을 선택해 주세요."));

		mockMvc.perform(get("/compare"))
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attribute("error", "비교할 지역을 선택해 주세요."));
	}

	private void assertRedirectWithError(Reason reason, String message) throws Exception {
		willThrow(new InvalidComparisonRequestException(reason, "내부 메시지"))
				.given(comparisonService).compare(SEOUL, CART);

		mockMvc.perform(get("/compare").param("areaCode", SEOUL))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attribute("error", message));
	}

	private void givenResult(List<CompleteStore> completeStores, List<PartialStore> partialStores) {
		given(comparisonService.compare(SEOUL, CART))
				.willReturn(new ComparisonResult(INSPECT_DAY, SEOUL, "서울특별시", completeStores, partialStores));
	}

	private String render() throws Exception {
		return mockMvc.perform(get("/compare").param("areaCode", SEOUL))
				.andExpect(status().isOk())
				.andExpect(view().name("compare"))
				.andReturn().getResponse().getContentAsString();
	}

	private static String section(String html, String id) {
		int start = html.indexOf("id=\"" + id + "\"");
		assertThat(start).as("section %s", id).isNotNegative();
		return html.substring(start, html.indexOf("</section>", start));
	}

	private static CompleteStore complete(long entpId, String name, String address, int ramen, int rice, int milk) {
		List<ItemPrice> items = List.of(
				new ItemPrice(RAMEN, ramen, 2, ramen * 2L),
				new ItemPrice(RICE, rice, 1, rice),
				new ItemPrice(MILK, milk, 1, milk));
		return new CompleteStore(entpId, name, address, items, ramen * 2L + rice + milk);
	}

	private static PartialStore partial(long entpId, String name, int ramenSubtotal) {
		return new PartialStore(entpId, name, "서울특별시 중구 명동길 2",
				List.of(new ItemPrice(RAMEN, ramenSubtotal / 2, 2, ramenSubtotal)),
				List.of(RICE, MILK), ramenSubtotal);
	}
}
