package com.martpick.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.martpick.cart.CartService;
import com.martpick.compare.InvalidComparisonRequestException.Reason;
import com.martpick.config.SecurityConfig;
import com.martpick.member.MemberUserDetails;

@WebMvcTest(CompareController.class)
@Import(SecurityConfig.class)
class CompareControllerTest {

	private static final String SEOUL = "020100000";
	private static final String BUSAN = "020500000";
	private static final LocalDate INSPECT_DAY = LocalDate.of(2026, 9, 25);
	private static final ProductInfo RAMEN = new ProductInfo(1000L, "팔도 왕뚜껑(110g)", "110", "G");
	private static final ProductInfo RICE = new ProductInfo(292L, "햇반(210g)", "210", "G");
	private static final ProductInfo MILK = new ProductInfo(224L, "서울우유 흰우유(1L)", "1000", "ML");
	private static final ProductInfo NO_PRICE_A = new ProductInfo(1274L, "때가 쏙 비트 분말세제(6kg)", "6000", "G");
	private static final ProductInfo NO_PRICE_B = new ProductInfo(1320L, "크리넥스 순수소프트 3겹 화장지(30롤)", "30", "EA");
	private static final ProductInfo ELSEWHERE_A = new ProductInfo(247L, "쇠고기 불고기(1+등급, 100g)", "100", "G");
	private static final ProductInfo ELSEWHERE_B = new ProductInfo(941L, "오징어(냉동, 200~300g)", "250", "G");
	private static final String MISSING_TITLE = "가격 정보가 없는 상품이 장바구니에 있습니다.";
	private static final String NATIONWIDE_LABEL = "기준 조사일에 전국 어느 판매점에서도 가격이 확인되지 않은 상품";
	private static final String REGION_LABEL = "서울특별시에서는 가격이 확인되지 않았지만 다른 지역에는 가격이 있는 상품";
	private static final List<CartLine> CART = List.of(new CartLine(1000L, 2), new CartLine(292L, 1), new CartLine(224L, 1));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ComparisonService comparisonService;

	@MockitoBean
	private CartService cartService;

	@BeforeEach
	void setUp() {
		given(cartService.lines(null)).willReturn(CART);
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

	@Test
	void 로그인한_회원이면_회원_장바구니로_비교한다() throws Exception {
		MemberUserDetails member = new MemberUserDetails(7L, "tester01", "hash");
		List<CartLine> memberCart = List.of(new CartLine(224L, 3));
		given(cartService.lines(member)).willReturn(memberCart);
		given(comparisonService.compare(SEOUL, memberCart))
				.willReturn(new ComparisonResult(INSPECT_DAY, SEOUL, "서울특별시", List.of(), List.of()));

		mockMvc.perform(get("/compare").with(user(member)).param("areaCode", SEOUL).param("memberId", "999"))
				.andExpect(status().isOk());

		then(cartService).should().lines(member);
		then(comparisonService).should().compare(SEOUL, memberCart);
		then(comparisonService).should(never()).compare(SEOUL, CART);
	}

	@Test
	void 결과_화면_상단에_지역_선택과_다시_비교_버튼을_보여_주고_현재_지역을_선택해_둔다() throws Exception {
		given(comparisonService.comparableRegions())
				.willReturn(List.of(new RegionOption(SEOUL, "서울특별시"), new RegionOption(BUSAN, "부산광역시")));
		givenResult(List.of(complete(100L, "가마트", "주소", 1000, 1000, 1000)), List.of());

		String form = regionForm(render());

		assertThat(form).contains("action=\"/compare\"", "method=\"get\"", "name=\"areaCode\"", "다시 비교");
		assertThat(form).contains("<option value=\"020100000\" selected=\"selected\">서울특별시</option>");
		assertThat(form).contains("<option value=\"020500000\">부산광역시</option>");
		assertThat(form).doesNotContain("value=\"\"").doesNotContain("_csrf");
	}

	@Test
	void 다른_지역을_고르면_같은_장바구니로_그_지역을_다시_비교하고_그_지역을_선택해_둔다() throws Exception {
		given(comparisonService.comparableRegions())
				.willReturn(List.of(new RegionOption(SEOUL, "서울특별시"), new RegionOption(BUSAN, "부산광역시")));
		given(comparisonService.compare(BUSAN, CART)).willReturn(new ComparisonResult(INSPECT_DAY, BUSAN, "부산광역시",
				List.of(complete(300L, "부산마트", "부산 주소", 900, 900, 900)), List.of()));

		String html = mockMvc.perform(get("/compare").param("areaCode", BUSAN))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		then(comparisonService).should().compare(BUSAN, CART);
		assertThat(html).contains("부산마트").contains("<strong>부산광역시</strong>");
		assertThat(regionForm(html)).contains("<option value=\"020500000\" selected=\"selected\">부산광역시</option>")
				.contains("<option value=\"020100000\">서울특별시</option>");
	}

	@Test
	void 지역_선택은_로그인한_회원의_결과_화면에도_같은_방식으로_보인다() throws Exception {
		MemberUserDetails member = new MemberUserDetails(7L, "tester01", "hash");
		List<CartLine> memberCart = List.of(new CartLine(224L, 3));
		given(cartService.lines(member)).willReturn(memberCart);
		given(comparisonService.comparableRegions()).willReturn(List.of(new RegionOption(SEOUL, "서울특별시")));
		given(comparisonService.compare(SEOUL, memberCart))
				.willReturn(new ComparisonResult(INSPECT_DAY, SEOUL, "서울특별시", List.of(), List.of()));

		String html = mockMvc.perform(get("/compare").with(user(member)).param("areaCode", SEOUL))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(regionForm(html)).contains("action=\"/compare\"", "다시 비교");
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 9, 10})
	void 완전_비교가_10곳_이하이면_펼치기_없이_모두_보여_준다(int count) throws Exception {
		List<CompleteStore> stores = completeStores(count);
		givenResult(stores, List.of());

		String html = render();
		String section = section(html, "complete-stores");

		assertThat(storeNames(section)).containsExactlyElementsOf(names(stores));
		assertThat(section).doesNotContain(MORE_MARKER).doesNotContain("나머지").doesNotContain("처음 10곳");
		assertThat(html).contains("완전 비교 " + count + "곳");
	}

	@ParameterizedTest
	@ValueSource(ints = {11, 12, 25, 105})
	void 완전_비교가_11곳_이상이면_처음_10곳만_보여_주고_나머지는_펼쳐_본다(int count) throws Exception {
		List<CompleteStore> stores = completeStores(count);
		givenResult(stores, List.of());

		String html = render();
		String section = section(html, "complete-stores");
		int more = section.indexOf(MORE_MARKER);

		assertThat(more).isPositive();
		assertThat(section.indexOf(MORE_MARKER, more + 1)).as("펼치기는 영역마다 하나").isNegative();
		assertThat(storeNames(section.substring(0, more))).containsExactlyElementsOf(names(stores).subList(0, 10));
		assertThat(storeNames(section.substring(more))).containsExactlyElementsOf(names(stores).subList(10, count));
		assertThat(section).contains("나머지 " + (count - 10) + "곳 보기");
		assertThat(section).contains("처음 10곳");
		assertThat(section.substring(more, more + MORE_MARKER.length() + 6)).doesNotContain("open");
		assertThat(countOf(section, "<li class=\"store-card")).isEqualTo(count);
		assertThat(html).contains("완전 비교 " + count + "곳");
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 10})
	void 부분_비교가_10곳_이하이면_펼치기_없이_모두_보여_준다(int count) throws Exception {
		List<PartialStore> stores = partialStores(count);
		givenResult(List.of(complete(100L, "가마트", "주소", 1000, 1000, 1000)), stores);

		String html = render();
		String section = section(html, "partial-stores");

		assertThat(storeNames(section)).containsExactlyElementsOf(partialNames(stores));
		assertThat(section).doesNotContain(MORE_MARKER).doesNotContain("나머지");
		assertThat(html).contains("부분 비교 " + count + "곳");
	}

	@ParameterizedTest
	@ValueSource(ints = {11, 25})
	void 부분_비교가_11곳_이상이면_기존_정렬_그대로_처음_10곳만_보여_주고_나머지는_펼쳐_본다(int count) throws Exception {
		List<PartialStore> stores = partialStores(count);
		givenResult(List.of(), stores);

		String html = render();
		String section = section(html, "partial-stores");
		int more = section.indexOf(MORE_MARKER);

		assertThat(more).isPositive();
		assertThat(storeNames(section.substring(0, more))).containsExactlyElementsOf(partialNames(stores).subList(0, 10));
		assertThat(storeNames(section.substring(more))).containsExactlyElementsOf(partialNames(stores).subList(10, count));
		assertThat(section).contains("나머지 " + (count - 10) + "곳 보기");
		assertThat(countOf(section, "<li class=\"store-card")).isEqualTo(count);
		assertThat(html).doesNotContain("<ol").doesNotContainPattern("\\d+위");
	}

	@Test
	void 완전_비교와_부분_비교가_모두_많아도_각_영역에서_따로_접고_전체_판매점을_남긴다() throws Exception {
		List<CompleteStore> completes = completeStores(30);
		List<PartialStore> partials = partialStores(15);
		givenResult(completes, partials);

		String html = render();

		assertThat(countOf(html, MORE_MARKER)).isEqualTo(2);
		assertThat(countOf(html, "<li class=\"store-card")).isEqualTo(45);
		assertThat(html).contains("완전 비교 30곳", "부분 비교 15곳", "나머지 20곳 보기", "나머지 5곳 보기");
		assertThat(html).doesNotContain("<ol").doesNotContainPattern("\\d+위");
		assertThat(storeNames(section(html, "complete-stores"))).containsExactlyElementsOf(names(completes));
		assertThat(storeNames(section(html, "partial-stores"))).containsExactlyElementsOf(partialNames(partials));
	}

	@Test
	void 가격_부재_상품이_없으면_안내를_표시하지_않는다() throws Exception {
		givenResult(List.of(complete(100L, "가마트", "주소", 1000, 1000, 1000)), List.of());

		assertThat(render()).doesNotContain(MISSING_TITLE).doesNotContain(NATIONWIDE_LABEL).doesNotContain("다른 지역에는 가격이 있는 상품");
	}

	@Test
	void 전국_가격_부재_상품은_이름과_함께_전국_기준으로_안내한다() throws Exception {
		givenMissing(List.of(), List.of(partial(200L, "나마트", 100)), List.of(NO_PRICE_A), List.of());

		String notice = missingNotice(render());

		assertThat(notice).contains(MISSING_TITLE, NATIONWIDE_LABEL, "때가 쏙 비트 분말세제(6kg)", "6000 G");
		assertThat(notice).doesNotContain("다른 지역에는 가격이 있는 상품");
		assertThat(notice).contains("판매하지 않는다는 뜻은 아닙니다").contains("href=\"/cart\"");
	}

	@Test
	void 지역_가격_부재_상품은_선택_지역_이름과_함께_지역_기준으로_안내한다() throws Exception {
		givenMissing(List.of(), List.of(partial(200L, "나마트", 100)), List.of(), List.of(ELSEWHERE_A));

		String notice = missingNotice(render());

		assertThat(notice).contains(MISSING_TITLE, REGION_LABEL, "쇠고기 불고기(1+등급, 100g)");
		assertThat(notice).doesNotContain(NATIONWIDE_LABEL);
	}

	@Test
	void 전국_부재와_지역_부재가_여러_개일_때_각각_자기_그룹에_모두_표시한다() throws Exception {
		givenMissing(List.of(), List.of(partial(200L, "나마트", 100)),
				List.of(NO_PRICE_A, NO_PRICE_B), List.of(ELSEWHERE_A, ELSEWHERE_B));

		String notice = missingNotice(render());
		int national = notice.indexOf(NATIONWIDE_LABEL);
		int region = notice.indexOf(REGION_LABEL);

		assertThat(national).isNotNegative();
		assertThat(region).isGreaterThan(national);
		String nationalGroup = notice.substring(national, region);
		String regionGroup = notice.substring(region);
		assertThat(nationalGroup).contains("때가 쏙 비트 분말세제(6kg)", "크리넥스 순수소프트 3겹 화장지(30롤)")
				.doesNotContain("쇠고기 불고기").doesNotContain("오징어");
		assertThat(regionGroup).contains("쇠고기 불고기(1+등급, 100g)", "오징어(냉동, 200~300g)")
				.doesNotContain("때가 쏙").doesNotContain("크리넥스");
		assertThat(nationalGroup.indexOf("때가 쏙")).isLessThan(nationalGroup.indexOf("크리넥스"));
	}

	@Test
	void 완전_비교가_0곳이어도_가격_부재_안내와_기존_안내를_함께_보여_준다() throws Exception {
		givenMissing(List.of(), List.of(partial(200L, "나마트", 100)), List.of(NO_PRICE_A), List.of());
		String html = render();

		assertThat(html).contains("선택한 지역에는 장바구니의 모든 상품의 가격이 확인된 판매점이 없습니다.").contains(MISSING_TITLE);
		assertThat(html).contains("나마트").doesNotContain("id=\"complete-stores\"");

		givenMissing(List.of(), List.of(), List.of(NO_PRICE_A), List.of(ELSEWHERE_A));
		String noPriced = render();

		assertThat(noPriced).contains("선택한 지역에는 장바구니 상품의 가격이 확인된 판매점이 없습니다.").contains(MISSING_TITLE);
		assertThat(missingNotice(noPriced)).contains("때가 쏙 비트 분말세제(6kg)", "쇠고기 불고기(1+등급, 100g)");
	}

	@Test
	void 가격_부재_안내가_있어도_판매점_결과와_정렬과_총액은_그대로_표시하고_장바구니를_바꾸지_않는다() throws Exception {
		List<CompleteStore> completes = List.of(
				complete(300L, "다마트", "주소", 1000, 1000, 1000),
				complete(100L, "가마트", "주소", 1000, 1000, 1100));
		givenMissing(completes, List.of(partial(900L, "하마트", 9000), partial(200L, "나마트", 100)),
				List.of(NO_PRICE_A), List.of(ELSEWHERE_A));

		String html = render();

		assertThat(html.indexOf("다마트")).isLessThan(html.indexOf("가마트"));
		assertThat(html.indexOf("하마트")).isLessThan(html.indexOf("나마트"));
		assertThat(html).contains("완전 비교 2곳", "부분 비교 2곳", "4,000원", "4,100원").doesNotContain("<ol");
		then(cartService).should(never()).remove(any(), anyLong());
		then(cartService).should(never()).changeQuantity(any(), anyLong(), anyInt());
	}

	@Test
	void 가격_부재_상품_이름의_HTML은_이스케이프한다() throws Exception {
		givenMissing(List.of(), List.of(), List.of(new ProductInfo(1L, "<b>위험</b> 상품", "1", "EA")), List.of());

		String notice = missingNotice(render());

		assertThat(notice).contains("&lt;b&gt;위험&lt;/b&gt; 상품").doesNotContain("<b>위험");
	}

	private void givenMissing(List<CompleteStore> completeStores, List<PartialStore> partialStores,
			List<ProductInfo> noPriceNationwide, List<ProductInfo> noPriceInRegion) {
		given(comparisonService.compare(SEOUL, CART)).willReturn(new ComparisonResult(INSPECT_DAY, SEOUL, "서울특별시",
				completeStores, partialStores, noPriceNationwide, noPriceInRegion));
	}

	private static String missingNotice(String html) {
		int start = html.indexOf("missing-notice");
		assertThat(start).as("가격 부재 안내 영역").isNotNegative();
		int end = html.length();
		for (String stop : new String[] {"<section class=\"result-section", "</main>"}) {
			int index = html.indexOf(stop, start);
			if (index > 0) {
				end = Math.min(end, index);
			}
		}
		return html.substring(start, end);
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

	private static final String MORE_MARKER = "class=\"price-details store-more\"";
	private static final Pattern STORE_NAME = Pattern.compile("<h3 class=\"store-card__name\"[^>]*>([^<]*)</h3>");

	private static List<CompleteStore> completeStores(int count) {
		return IntStream.range(0, count)
				.mapToObj(i -> complete(1000L + i, String.format("완전마트%03d", i), "주소", 1000, 1000, 1000 + i))
				.toList();
	}

	private static List<PartialStore> partialStores(int count) {
		return IntStream.range(0, count)
				.mapToObj(i -> partial(5000L + i, String.format("부분마트%03d", i), 100 + 2 * i))
				.toList();
	}

	private static List<String> names(List<CompleteStore> stores) {
		return stores.stream().map(CompleteStore::name).toList();
	}

	private static List<String> partialNames(List<PartialStore> stores) {
		return stores.stream().map(PartialStore::name).toList();
	}

	private static List<String> storeNames(String html) {
		Matcher matcher = STORE_NAME.matcher(html);
		List<String> names = new ArrayList<>();
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return names;
	}

	private static int countOf(String html, String token) {
		return (html.length() - html.replace(token, "").length()) / token.length();
	}

	private static String regionForm(String html) {
		int start = html.indexOf("region-switch");
		assertThat(start).as("지역 변경 폼").isNotNegative();
		return html.substring(start, html.indexOf("</form>", start));
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
