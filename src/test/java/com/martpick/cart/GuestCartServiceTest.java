package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.compare.CartLine;

/**
 * 세션 범위 빈은 웹 요청 없이 만들 수 없으므로 {@link GuestCart}를 직접 생성해 서비스에 넘긴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class GuestCartServiceTest {

	private static final long CUP_ID = 9_000_101L;
	private static final long MILK_ID = 9_000_102L;
	private static final long NOT_COLLECTED_ID = 9_000_199L;

	@Autowired
	private ProductRepository productRepository;

	private GuestCart cart;
	private GuestCartService service;

	@BeforeEach
	void setUp() {
		productRepository.save(new Product(CUP_ID, "마트픽테스트 컵라면(110g)", "110", "G"));
		productRepository.save(new Product(MILK_ID, "마트픽테스트 흰우유(1L)", "1000", "ML"));
		productRepository.flush();
		cart = new GuestCart();
		service = new GuestCartService(cart, productRepository);
	}

	@Test
	void 수집된_상품을_담고_상품명을_반환한다() {
		String name = service.add(CUP_ID);

		assertThat(name).isEqualTo("마트픽테스트 컵라면(110g)");
		assertThat(cart.lines()).containsExactly(new CartLine(CUP_ID, 1));
	}

	@Test
	void 수집되지_않은_상품은_담을_수_없다() {
		for (long goodId : new long[] {NOT_COLLECTED_ID, 0L, -1L}) {
			assertThatThrownBy(() -> service.add(goodId)).isInstanceOf(InvalidCartRequestException.class);
		}
		assertThat(cart.isEmpty()).isTrue();
	}

	@Test
	void 장바구니_상품을_담은_순서대로_상품명_규격_수량과_함께_조회한다() {
		service.add(MILK_ID);
		service.add(CUP_ID);
		service.add(MILK_ID);

		assertThat(service.items()).containsExactly(
				new CartItemView(MILK_ID, "마트픽테스트 흰우유(1L)", "1000 ML", 2),
				new CartItemView(CUP_ID, "마트픽테스트 컵라면(110g)", "110 G", 1));
	}

	@Test
	void 빈_장바구니는_빈_목록을_반환한다() {
		assertThat(service.items()).isEmpty();
	}

	@Test
	void 수량을_변경하고_삭제한다() {
		service.add(CUP_ID);
		service.add(MILK_ID);

		service.changeQuantity(CUP_ID, 7);
		service.remove(MILK_ID);

		assertThat(cart.lines()).containsExactly(new CartLine(CUP_ID, 7));
	}
}
