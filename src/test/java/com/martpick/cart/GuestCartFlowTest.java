package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;

/**
 * 실제 세션 범위 빈으로 비회원 장바구니 흐름과 세션 분리를 확인한다. 테스트 상품은 트랜잭션 롤백으로 남기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GuestCartFlowTest {

	private static final long CUP_ID = 9_000_201L;
	private static final long MILK_ID = 9_000_202L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository productRepository;

	@BeforeEach
	void setUp() {
		productRepository.save(new Product(CUP_ID, "마트픽테스트 컵라면(110g)", "110", "G"));
		productRepository.save(new Product(MILK_ID, "마트픽테스트 흰우유(1L)", "1000", "ML"));
		productRepository.flush();
	}

	@Test
	void 담기_수량_변경_삭제가_같은_세션의_장바구니에_반영된다() throws Exception {
		MockHttpSession session = new MockHttpSession();

		addToCart(session, CUP_ID);
		addToCart(session, CUP_ID);
		addToCart(session, MILK_ID);
		assertThat(cartItems(session)).containsExactly(
				new CartItemView(CUP_ID, "마트픽테스트 컵라면(110g)", "110 G", 2),
				new CartItemView(MILK_ID, "마트픽테스트 흰우유(1L)", "1000 ML", 1));

		mockMvc.perform(post("/cart/items/" + MILK_ID + "/quantity").session(session).with(csrf()).param("quantity", "99"));
		mockMvc.perform(post("/cart/items/" + CUP_ID + "/delete").session(session).with(csrf()));

		assertThat(cartItems(session)).containsExactly(
				new CartItemView(MILK_ID, "마트픽테스트 흰우유(1L)", "1000 ML", 99));
	}

	@Test
	void 세션이_다르면_장바구니가_섞이지_않는다() throws Exception {
		MockHttpSession first = new MockHttpSession();
		MockHttpSession second = new MockHttpSession();

		addToCart(first, CUP_ID);
		addToCart(second, MILK_ID);
		addToCart(second, MILK_ID);

		assertThat(cartItems(first)).extracting(CartItemView::goodId, CartItemView::quantity)
				.containsExactly(tuple(CUP_ID, 1));
		assertThat(cartItems(second)).extracting(CartItemView::goodId, CartItemView::quantity)
				.containsExactly(tuple(MILK_ID, 2));
		assertThat(cartItems(new MockHttpSession())).isEmpty();
	}

	private void addToCart(MockHttpSession session, long goodId) throws Exception {
		mockMvc.perform(post("/cart/items").session(session).with(csrf()).param("goodId", String.valueOf(goodId)));
	}

	@SuppressWarnings("unchecked")
	private List<CartItemView> cartItems(MockHttpSession session) throws Exception {
		return (List<CartItemView>) mockMvc.perform(get("/cart").session(session))
				.andReturn().getModelAndView().getModel().get("items");
	}
}
