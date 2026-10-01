package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.martpick.catalog.Price;
import com.martpick.catalog.PriceRepository;
import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.catalog.Region;
import com.martpick.catalog.RegionRepository;
import com.martpick.catalog.Store;
import com.martpick.catalog.StoreRepository;
import com.martpick.compare.ComparisonResult;
import com.martpick.member.MemberRepository;
import com.martpick.member.MemberService;

import jakarta.persistence.EntityManager;

/**
 * 실제 Spring Security 필터 체인과 PostgreSQL을 거치는 회원·비회원 장바구니 흐름.
 * 비회원은 세션, 로그인한 회원은 DB 장바구니를 쓰고 서로 섞이지 않는지 확인한다. 테스트 데이터는 롤백으로 남기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberCartFlowTest {

	private static final String AREA_CODE = "029800000";
	private static final LocalDate INSPECT_DAY = LocalDate.of(2026, 9, 25);
	private static final long CUP_ID = 9_000_801L;
	private static final long MILK_ID = 9_000_802L;
	private static final long COMPLETE_STORE_ID = 9_999_999_401L;
	private static final long PARTIAL_STORE_ID = 9_999_999_402L;
	private static final String PASSWORD_A = "password-A-1234";
	private static final String PASSWORD_B = "password-B-5678";
	private static final String GUEST_CART_ATTRIBUTE = "scopedTarget.guestCart";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberService memberService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private PriceRepository priceRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager em;

	private long memberA;
	private long memberB;

	@BeforeEach
	void setUp() {
		memberService.signup("flowCartA", PASSWORD_A);
		memberService.signup("flowCartB", PASSWORD_B);
		memberA = memberRepository.findByUsername("flowCartA").orElseThrow().getId();
		memberB = memberRepository.findByUsername("flowCartB").orElseThrow().getId();

		regionRepository.save(new Region(AREA_CODE, "테스트시"));
		Product cup = productRepository.save(new Product(CUP_ID, "마트픽회원흐름 컵라면(110g)", "110", "G"));
		Product milk = productRepository.save(new Product(MILK_ID, "마트픽회원흐름 흰우유(1L)", "1000", "ML"));
		Store complete = storeRepository.save(store(COMPLETE_STORE_ID, "테스트완전마트"));
		Store partial = storeRepository.save(store(PARTIAL_STORE_ID, "테스트부분마트"));
		priceRepository.save(new Price(complete, cup, INSPECT_DAY, 1280));
		priceRepository.save(new Price(complete, milk, INSPECT_DAY, 2890));
		priceRepository.save(new Price(partial, cup, INSPECT_DAY, 1300));
		priceRepository.flush();
	}

	// ---- 1. 비회원 ----

	@Test
	void 비회원의_담기_조회_수량_변경_삭제는_세션에만_반영되고_DB_장바구니는_변하지_않는다() throws Exception {
		long before = totalCartRows();
		MockHttpSession guest = new MockHttpSession();

		add(guest, CUP_ID);
		add(guest, CUP_ID);
		add(guest, MILK_ID);
		assertThat(items(guest)).extracting(CartItemView::goodId, CartItemView::quantity)
				.containsExactly(tuple(CUP_ID, 2), tuple(MILK_ID, 1));

		mockMvc.perform(post("/cart/items/" + MILK_ID + "/quantity").session(guest).with(csrf()).param("quantity", "9"));
		mockMvc.perform(post("/cart/items/" + CUP_ID + "/delete").session(guest).with(csrf()));

		assertThat(items(guest)).extracting(CartItemView::goodId, CartItemView::quantity)
				.containsExactly(tuple(MILK_ID, 9));
		assertThat(guestCart(guest).lines()).hasSize(1);
		assertThat(totalCartRows()).isEqualTo(before);
	}

	// ---- 2. 회원 ----

	@Test
	void 회원의_담기_조회_수량_변경_삭제는_DB에_반영된다() throws Exception {
		MockHttpSession member = login("flowCartA", PASSWORD_A, null);

		add(member, CUP_ID);
		add(member, CUP_ID);
		add(member, MILK_ID);
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(2);
		assertThat(quantityInDb(memberA, MILK_ID)).isEqualTo(1);
		assertThat(items(member)).containsExactly(
				new CartItemView(CUP_ID, "마트픽회원흐름 컵라면(110g)", "110 G", 2),
				new CartItemView(MILK_ID, "마트픽회원흐름 흰우유(1L)", "1000 ML", 1));

		mockMvc.perform(post("/cart/items/" + MILK_ID + "/quantity").session(member).with(csrf()).param("quantity", "9"));
		assertThat(quantityInDb(memberA, MILK_ID)).isEqualTo(9);

		mockMvc.perform(post("/cart/items/" + CUP_ID + "/delete").session(member).with(csrf()));
		assertThat(quantityInDb(memberA, CUP_ID)).isNull();
		assertThat(items(member)).extracting(CartItemView::goodId, CartItemView::quantity)
				.containsExactly(tuple(MILK_ID, 9));
	}

	@Test
	void 회원이_담는_동안_세션_장바구니는_만들어지거나_바뀌지_않는다() throws Exception {
		MockHttpSession member = login("flowCartA", PASSWORD_A, null);

		add(member, CUP_ID);
		items(member);

		assertThat(member.getAttribute(GUEST_CART_ATTRIBUTE)).isNull();
	}

	// ---- 3, 4. 자동 병합 없음, 로그인 중 세션 장바구니 미사용 ----

	@Test
	void 로그인_전_비회원으로_담은_상품은_로그인_후_회원_장바구니로_병합되지_않는다() throws Exception {
		MockHttpSession guest = new MockHttpSession();
		add(guest, CUP_ID);
		add(guest, MILK_ID);

		MockHttpSession member = login("flowCartA", PASSWORD_A, guest);

		assertThat(items(member)).isEmpty();
		assertThat(rowsInDb(memberA)).isZero();
		assertThat(totalCartRows()).isZero();
	}

	@Test
	void 로그인_중에는_세션_장바구니가_조회_변경_삭제_비교에_쓰이지_않고_그대로_남는다() throws Exception {
		MockHttpSession guest = new MockHttpSession();
		add(guest, CUP_ID);
		add(guest, CUP_ID);
		MockHttpSession member = login("flowCartA", PASSWORD_A, guest);
		add(member, MILK_ID);

		assertThat(items(member)).extracting(CartItemView::goodId).containsExactly(MILK_ID);
		mockMvc.perform(post("/cart/items/" + CUP_ID + "/quantity").session(member).with(csrf()).param("quantity", "7"));
		mockMvc.perform(post("/cart/items/" + CUP_ID + "/delete").session(member).with(csrf()));
		ComparisonResult result = compare(member);

		assertThat(result.completeStores()).singleElement().satisfies(store ->
				assertThat(store.items()).extracting(item -> item.product().goodId()).containsExactly(MILK_ID));
		assertThat(guestCart(member).lines()).containsExactly(new com.martpick.compare.CartLine(CUP_ID, 2));
		assertThat(rowsInDb(memberA)).isEqualTo(1);
	}

	// ---- 5. 로그아웃·재로그인 ----

	@Test
	void 로그아웃해도_회원_DB_장바구니는_유지되고_재로그인하면_다시_조회된다() throws Exception {
		MockHttpSession member = login("flowCartA", PASSWORD_A, null);
		add(member, CUP_ID);
		add(member, MILK_ID);
		mockMvc.perform(post("/cart/items/" + MILK_ID + "/quantity").session(member).with(csrf()).param("quantity", "4"));

		mockMvc.perform(post("/logout").session(member).with(csrf())).andExpect(redirectedUrl("/products"));
		assertThat(member.isInvalid()).isTrue();
		assertThat(rowsInDb(memberA)).isEqualTo(2);

		assertThat(items(new MockHttpSession())).isEmpty();

		MockHttpSession again = login("flowCartA", PASSWORD_A, null);
		assertThat(items(again)).extracting(CartItemView::goodId, CartItemView::quantity)
				.containsExactly(tuple(CUP_ID, 1), tuple(MILK_ID, 4));
	}

	@Test
	void 로그아웃_뒤의_비회원_장바구니는_새로_시작하고_이전_세션_장바구니는_남지_않는다() throws Exception {
		MockHttpSession guest = new MockHttpSession();
		add(guest, CUP_ID);
		MockHttpSession member = login("flowCartA", PASSWORD_A, guest);

		mockMvc.perform(post("/logout").session(member).with(csrf()));

		assertThat(items(new MockHttpSession())).isEmpty();
		assertThat(rowsInDb(memberA)).isZero();
	}

	// ---- 6, 7. 회원 간 격리, 요청의 회원 ID 무시, 비회원의 DB 접근 불가 ----

	@Test
	void 회원_A와_B가_같은_상품을_담아도_수량_변경과_삭제가_서로에게_영향을_주지_않는다() throws Exception {
		MockHttpSession a = login("flowCartA", PASSWORD_A, null);
		MockHttpSession b = login("flowCartB", PASSWORD_B, null);
		add(a, CUP_ID);
		add(b, CUP_ID);
		mockMvc.perform(post("/cart/items/" + CUP_ID + "/quantity").session(b).with(csrf()).param("quantity", "8"));

		mockMvc.perform(post("/cart/items/" + CUP_ID + "/quantity").session(a).with(csrf()).param("quantity", "3"));
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(3);
		assertThat(quantityInDb(memberB, CUP_ID)).isEqualTo(8);

		mockMvc.perform(post("/cart/items/" + CUP_ID + "/delete").session(a).with(csrf()));
		assertThat(quantityInDb(memberA, CUP_ID)).isNull();
		assertThat(quantityInDb(memberB, CUP_ID)).isEqualTo(8);
		assertThat(items(a)).isEmpty();
		assertThat(items(b)).extracting(CartItemView::quantity).containsExactly(8);
	}

	@Test
	void 요청에_임의의_memberId를_넣어도_다른_회원의_장바구니에는_접근할_수_없다() throws Exception {
		MockHttpSession a = login("flowCartA", PASSWORD_A, null);
		MockHttpSession b = login("flowCartB", PASSWORD_B, null);
		add(b, MILK_ID);
		mockMvc.perform(post("/cart/items/" + MILK_ID + "/quantity").session(b).with(csrf()).param("quantity", "8"));

		mockMvc.perform(post("/cart/items").session(a).with(csrf())
				.param("goodId", String.valueOf(CUP_ID)).param("memberId", String.valueOf(memberB))
				.param("member", String.valueOf(memberB)).param("id", String.valueOf(memberB)));
		mockMvc.perform(post("/cart/items/" + MILK_ID + "/quantity").session(a).with(csrf())
				.param("quantity", "1").param("memberId", String.valueOf(memberB)));
		mockMvc.perform(post("/cart/items/" + MILK_ID + "/delete").session(a).with(csrf())
				.param("memberId", String.valueOf(memberB)));
		MvcResult cart = mockMvc.perform(get("/cart").session(a).param("memberId", String.valueOf(memberB)))
				.andReturn();

		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(1);
		assertThat(quantityInDb(memberB, CUP_ID)).isNull();
		assertThat(quantityInDb(memberB, MILK_ID)).isEqualTo(8);
		assertThat(cartItemViews(cart)).extracting(CartItemView::goodId).containsExactly(CUP_ID);
	}

	@Test
	void 비회원은_DB_장바구니에_접근할_수_없고_요청의_memberId도_무시된다() throws Exception {
		MockHttpSession b = login("flowCartB", PASSWORD_B, null);
		add(b, MILK_ID);
		mockMvc.perform(post("/cart/items/" + MILK_ID + "/quantity").session(b).with(csrf()).param("quantity", "8"));
		long before = totalCartRows();
		MockHttpSession guest = new MockHttpSession();

		assertThat(items(guest)).isEmpty();
		mockMvc.perform(post("/cart/items").session(guest).with(csrf())
				.param("goodId", String.valueOf(CUP_ID)).param("memberId", String.valueOf(memberB)));
		mockMvc.perform(post("/cart/items/" + MILK_ID + "/quantity").session(guest).with(csrf())
				.param("quantity", "1").param("memberId", String.valueOf(memberB)));
		mockMvc.perform(post("/cart/items/" + MILK_ID + "/delete").session(guest).with(csrf())
				.param("memberId", String.valueOf(memberB)));
		MvcResult cart = mockMvc.perform(get("/cart").session(guest).param("memberId", String.valueOf(memberB)))
				.andReturn();

		assertThat(quantityInDb(memberB, MILK_ID)).isEqualTo(8);
		assertThat(totalCartRows()).isEqualTo(before);
		assertThat(cartItemViews(cart)).extracting(CartItemView::goodId).containsExactly(CUP_ID);
	}

	// ---- 8. 가격 비교 일치 ----

	@Test
	void 회원_장바구니로_비교한_결과는_같은_상품과_수량의_비회원_비교_결과와_같다() throws Exception {
		MockHttpSession guest = new MockHttpSession();
		add(guest, CUP_ID);
		add(guest, CUP_ID);
		add(guest, MILK_ID);
		ComparisonResult guestResult = compare(guest);

		MockHttpSession member = login("flowCartA", PASSWORD_A, null);
		add(member, CUP_ID);
		add(member, CUP_ID);
		add(member, MILK_ID);
		ComparisonResult memberResult = compare(member);

		assertThat(memberResult).isEqualTo(guestResult);
		assertThat(memberResult.completeStores()).singleElement().satisfies(store -> {
			assertThat(store.entpId()).isEqualTo(COMPLETE_STORE_ID);
			assertThat(store.totalPrice()).isEqualTo(1280L * 2 + 2890);
		});
		assertThat(memberResult.partialStores()).singleElement().satisfies(store -> {
			assertThat(store.entpId()).isEqualTo(PARTIAL_STORE_ID);
			assertThat(store.knownSubtotal()).isEqualTo(1300L * 2);
		});
	}

	// ---- 9. 기존 오류 처리 유지 ----

	@Test
	void 회원도_빈_장바구니_비교와_잘못된_지역은_기존과_같이_안내한다() throws Exception {
		MockHttpSession member = login("flowCartA", PASSWORD_A, null);

		mockMvc.perform(get("/compare").session(member).param("areaCode", AREA_CODE))
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attribute("error", "장바구니가 비어 있습니다. 비교할 상품을 담아 주세요."));

		add(member, CUP_ID);
		mockMvc.perform(get("/compare").session(member).param("areaCode", "029999999"))
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attribute("error", "비교할 수 없는 지역입니다. 목록에서 지역을 다시 선택해 주세요."));
		mockMvc.perform(get("/compare").session(member))
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attribute("error", "비교할 지역을 선택해 주세요."));
	}

	@Test
	void 회원도_잘못된_수량은_거부하고_기존_수량을_유지한다() throws Exception {
		MockHttpSession member = login("flowCartA", PASSWORD_A, null);
		add(member, CUP_ID);

		for (String quantity : new String[] {"0", "-1", "100", "1.5", "abc", ""}) {
			mockMvc.perform(post("/cart/items/" + CUP_ID + "/quantity").session(member).with(csrf()).param("quantity", quantity))
					.andExpect(redirectedUrl("/cart"))
					.andExpect(flash().attribute("error", "수량은 1~99 사이의 정수로 입력해 주세요."));
		}
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(1);
	}

	@Test
	void 회원도_99개를_넘는_담기는_거부하고_안내한다() throws Exception {
		MockHttpSession member = login("flowCartA", PASSWORD_A, null);
		add(member, CUP_ID);
		mockMvc.perform(post("/cart/items/" + CUP_ID + "/quantity").session(member).with(csrf()).param("quantity", "99"));

		mockMvc.perform(post("/cart/items").session(member).with(csrf()).param("goodId", String.valueOf(CUP_ID)))
				.andExpect(redirectedUrl("/products#product-" + CUP_ID))
				.andExpect(flash().attribute("error", "상품당 최대 99개까지 담을 수 있습니다."));
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(99);
	}

	@Test
	void 회원도_수집되지_않은_상품은_담을_수_없다() throws Exception {
		MockHttpSession member = login("flowCartA", PASSWORD_A, null);

		mockMvc.perform(post("/cart/items").session(member).with(csrf()).param("goodId", "9000899"))
				.andExpect(flash().attribute("error", "담을 수 없는 상품입니다."));
		assertThat(rowsInDb(memberA)).isZero();
	}

	@Test
	void CSRF_토큰이_없으면_회원의_장바구니_변경도_거부한다() throws Exception {
		MockHttpSession member = login("flowCartA", PASSWORD_A, null);

		mockMvc.perform(post("/cart/items").session(member).param("goodId", String.valueOf(CUP_ID)))
				.andExpect(status().isForbidden());

		assertThat(rowsInDb(memberA)).isZero();
	}

	// ---- 도우미 ----

	private MockHttpSession login(String username, String password, MockHttpSession existing) throws Exception {
		MockHttpServletRequestBuilder request = post("/login").with(csrf())
				.param("username", username).param("password", password);
		if (existing != null) {
			request.session(existing);
		}
		MvcResult result = mockMvc.perform(request).andExpect(redirectedUrl("/products")).andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private void add(MockHttpSession session, long goodId) throws Exception {
		mockMvc.perform(post("/cart/items").session(session).with(csrf()).param("goodId", String.valueOf(goodId)));
	}

	private List<CartItemView> items(MockHttpSession session) throws Exception {
		return cartItemViews(mockMvc.perform(get("/cart").session(session)).andReturn());
	}

	@SuppressWarnings("unchecked")
	private static List<CartItemView> cartItemViews(MvcResult result) {
		return (List<CartItemView>) result.getModelAndView().getModel().get("items");
	}

	private ComparisonResult compare(MockHttpSession session) throws Exception {
		MvcResult result = mockMvc.perform(get("/compare").session(session).param("areaCode", AREA_CODE))
				.andExpect(status().isOk()).andReturn();
		return (ComparisonResult) result.getModelAndView().getModel().get("result");
	}

	private static GuestCart guestCart(MockHttpSession session) {
		Object cart = session.getAttribute(GUEST_CART_ATTRIBUTE);
		assertThat(cart).as("세션 장바구니").isInstanceOf(GuestCart.class);
		return (GuestCart) cart;
	}

	private Integer quantityInDb(long memberId, long goodId) {
		em.flush();
		List<Integer> found = jdbc.queryForList(
				"select quantity from cart_item where member_id = ? and good_id = ?", Integer.class, memberId, goodId);
		return found.isEmpty() ? null : found.get(0);
	}

	private int rowsInDb(long memberId) {
		em.flush();
		return jdbc.queryForObject("select count(*) from cart_item where member_id = ?", Integer.class, memberId);
	}

	private int totalCartRows() {
		em.flush();
		return jdbc.queryForObject("select count(*) from cart_item", Integer.class);
	}

	private static Store store(long entpId, String name) {
		return new Store(entpId, name, "SM", AREA_CODE, "029801000",
				"테스트시 테스트로 " + (entpId % 100), null, "테스트시 테스트동 1", null, false, null);
	}
}
