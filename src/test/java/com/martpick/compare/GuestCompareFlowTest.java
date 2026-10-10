package com.martpick.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.martpick.catalog.Price;
import com.martpick.catalog.PriceRepository;
import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.catalog.Region;
import com.martpick.catalog.RegionRepository;
import com.martpick.catalog.Store;
import com.martpick.catalog.StoreRepository;

/**
 * 세션 장바구니에서 실제 {@link ComparisonService}까지 비회원 비교 흐름을 확인한다.
 * 테스트 DB에 테스트 전용 지역·판매점·상품·가격을 넣고 트랜잭션 롤백으로 남기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GuestCompareFlowTest {

	private static final String AREA_CODE = "029900000";
	private static final LocalDate INSPECT_DAY = LocalDate.of(2026, 9, 25);
	private static final String OTHER_AREA_CODE = "029800000";
	private static final long OTHER_STORE_ID = 9_999_999_303L;
	private static final long CUP_ID = 9_000_301L;
	private static final long MILK_ID = 9_000_302L;
	private static final long COMPLETE_STORE_ID = 9_999_999_301L;
	private static final long PARTIAL_STORE_ID = 9_999_999_302L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComparisonService comparisonService;

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private PriceRepository priceRepository;

	@BeforeEach
	void setUp() {
		regionRepository.save(new Region(AREA_CODE, "테스트시"));
		Product cup = productRepository.save(new Product(CUP_ID, "마트픽테스트 컵라면(110g)", "110", "G"));
		Product milk = productRepository.save(new Product(MILK_ID, "마트픽테스트 흰우유(1L)", "1000", "ML"));
		Store complete = storeRepository.save(store(COMPLETE_STORE_ID, "테스트완전마트"));
		Store partial = storeRepository.save(store(PARTIAL_STORE_ID, "테스트부분마트"));
		priceRepository.save(new Price(complete, cup, INSPECT_DAY, 1280));
		priceRepository.save(new Price(complete, milk, INSPECT_DAY, 2890));
		priceRepository.save(new Price(partial, cup, INSPECT_DAY, 1300));
		regionRepository.save(new Region(OTHER_AREA_CODE, "테스트군"));
		Store other = storeRepository.save(otherStore(OTHER_STORE_ID, "테스트군마트"));
		priceRepository.save(new Price(other, cup, INSPECT_DAY, 1500));
		priceRepository.save(new Price(other, milk, INSPECT_DAY, 3000));
		priceRepository.flush();
	}

	@Test
	void 세션_장바구니로_선택_지역을_비교해_완전_비교와_부분_비교를_구분해_표시한다() throws Exception {
		MockHttpSession session = new MockHttpSession();
		addToCart(session, CUP_ID);
		addToCart(session, CUP_ID);
		addToCart(session, MILK_ID);

		MvcResult mvcResult = mockMvc.perform(get("/compare").session(session).param("areaCode", AREA_CODE))
				.andExpect(status().isOk())
				.andReturn();

		ComparisonResult result = (ComparisonResult) mvcResult.getModelAndView().getModel().get("result");
		assertThat(result.areaName()).isEqualTo("테스트시");
		assertThat(result.completeStores()).singleElement().satisfies(store -> {
			assertThat(store.entpId()).isEqualTo(COMPLETE_STORE_ID);
			assertThat(store.totalPrice()).isEqualTo(1280L * 2 + 2890);
		});
		assertThat(result.partialStores()).singleElement().satisfies(store -> {
			assertThat(store.entpId()).isEqualTo(PARTIAL_STORE_ID);
			assertThat(store.knownSubtotal()).isEqualTo(1300L * 2);
		});
		String html = mvcResult.getResponse().getContentAsString();
		assertThat(html).contains("장바구니 합계", "5,450원", "확인된 상품 합계 2,600원 (마트픽테스트 흰우유(1L) 제외)");
	}

	@Test
	void 지역을_바꿔_다시_비교하면_그_지역의_결과와_일치하고_선택한_지역을_세션에_저장하지_않는다() throws Exception {
		MockHttpSession session = new MockHttpSession();
		addToCart(session, CUP_ID);
		addToCart(session, CUP_ID);
		addToCart(session, MILK_ID);

		String first = mockMvc.perform(get("/compare").session(session).param("areaCode", AREA_CODE))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(first).contains("테스트완전마트").doesNotContain("테스트군마트");
		Set<String> attributesBefore = new TreeSet<>(Collections.list(session.getAttributeNames()));

		MvcResult second = mockMvc.perform(get("/compare").session(session).param("areaCode", OTHER_AREA_CODE))
				.andExpect(status().isOk()).andReturn();

		ComparisonResult result = (ComparisonResult) second.getModelAndView().getModel().get("result");
		assertThat(result).isEqualTo(comparisonService.compare(OTHER_AREA_CODE,
				List.of(new CartLine(CUP_ID, 2), new CartLine(MILK_ID, 1))));
		assertThat(result.completeStores()).singleElement().satisfies(store -> {
			assertThat(store.entpId()).isEqualTo(OTHER_STORE_ID);
			assertThat(store.totalPrice()).isEqualTo(1500L * 2 + 3000);
		});
		String html = second.getResponse().getContentAsString();
		assertThat(html).contains("테스트군마트", "6,000원").doesNotContain("테스트완전마트");
		assertThat(html).contains("<option value=\"029800000\" selected=\"selected\">테스트군</option>",
				"<option value=\"029900000\">테스트시</option>");
		assertThat(new TreeSet<>(Collections.list(session.getAttributeNames())))
				.as("지역을 세션에 새로 저장하지 않는다").isEqualTo(attributesBefore);
	}

	@Test
	void 빈_장바구니로_비교하면_장바구니_화면으로_돌아가_안내한다() throws Exception {
		mockMvc.perform(get("/compare").session(new MockHttpSession()).param("areaCode", AREA_CODE))
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attribute("error", "장바구니가 비어 있습니다. 비교할 상품을 담아 주세요."));
	}

	@Test
	void 지역_목록에_없는_지역으로_비교하면_장바구니_화면으로_돌아가_안내한다() throws Exception {
		MockHttpSession session = new MockHttpSession();
		addToCart(session, CUP_ID);

		mockMvc.perform(get("/compare").session(session).param("areaCode", "029999999"))
				.andExpect(redirectedUrl("/cart"))
				.andExpect(flash().attribute("error", "비교할 수 없는 지역입니다. 목록에서 지역을 다시 선택해 주세요."));
	}

	private void addToCart(MockHttpSession session, long goodId) throws Exception {
		mockMvc.perform(post("/cart/items").session(session).with(csrf()).param("goodId", String.valueOf(goodId)));
	}

	private static Store otherStore(long entpId, String name) {
		return new Store(entpId, name, "SM", OTHER_AREA_CODE, "029801000",
				"테스트군 테스트로 " + (entpId % 100), null, "테스트군 테스트동 1", null, false, null);
	}

	private static Store store(long entpId, String name) {
		return new Store(entpId, name, "SM", AREA_CODE, "029901000",
				"테스트시 테스트로 " + (entpId % 100), null, "테스트시 테스트동 1", null, false, null);
	}
}
