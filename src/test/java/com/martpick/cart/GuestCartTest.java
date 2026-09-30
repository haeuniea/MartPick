package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.martpick.compare.CartLine;

class GuestCartTest {

	private final GuestCart cart = new GuestCart();

	@Test
	void 새_장바구니는_비어_있다() {
		assertThat(cart.isEmpty()).isTrue();
		assertThat(cart.lines()).isEmpty();
	}

	@Test
	void 처음_담은_상품은_1개다() {
		cart.add(1000L);

		assertThat(cart.isEmpty()).isFalse();
		assertThat(cart.lines()).containsExactly(new CartLine(1000L, 1));
	}

	@Test
	void 이미_담긴_상품을_담으면_1개_늘어난다() {
		cart.add(1000L);
		cart.add(1000L);

		assertThat(cart.lines()).containsExactly(new CartLine(1000L, 2));
	}

	@Test
	void 담은_순서를_유지한다() {
		cart.add(292L);
		cart.add(1000L);
		cart.add(224L);
		cart.add(292L);

		assertThat(cart.lines()).extracting(CartLine::goodId).containsExactly(292L, 1000L, 224L);
	}

	@Test
	void 수량이_99개인_상품은_더_담을_수_없고_수량을_유지한다() {
		cart.add(1000L);
		cart.changeQuantity(1000L, 99);

		assertThatThrownBy(() -> cart.add(1000L))
				.isInstanceOf(InvalidCartRequestException.class)
				.hasMessageContaining("99");
		assertThat(cart.lines()).containsExactly(new CartLine(1000L, 99));
	}

	@Test
	void 수량은_1부터_99까지_변경할_수_있다() {
		cart.add(1000L);

		cart.changeQuantity(1000L, 1);
		assertThat(cart.lines()).containsExactly(new CartLine(1000L, 1));

		cart.changeQuantity(1000L, 99);
		assertThat(cart.lines()).containsExactly(new CartLine(1000L, 99));
	}

	@Test
	void 범위를_벗어난_수량으로_변경하면_거부하고_기존_수량을_유지한다() {
		cart.add(1000L);
		cart.changeQuantity(1000L, 5);

		for (int quantity : new int[] {0, -1, 100}) {
			assertThatThrownBy(() -> cart.changeQuantity(1000L, quantity))
					.isInstanceOf(InvalidCartRequestException.class);
		}
		assertThat(cart.lines()).containsExactly(new CartLine(1000L, 5));
	}

	@Test
	void 담기지_않은_상품의_수량_변경은_무시한다() {
		cart.add(1000L);

		cart.changeQuantity(292L, 3);

		assertThat(cart.lines()).containsExactly(new CartLine(1000L, 1));
	}

	@Test
	void 상품을_삭제한다() {
		cart.add(1000L);
		cart.add(292L);

		cart.remove(1000L);
		cart.remove(9999L);

		assertThat(cart.lines()).containsExactly(new CartLine(292L, 1));
	}

	@Test
	void 세션_저장을_위해_직렬화할_수_있다() throws Exception {
		cart.add(1000L);
		cart.add(292L);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(cart);
		}
		GuestCart restored;
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			restored = (GuestCart) in.readObject();
		}

		assertThat(restored.lines()).containsExactly(new CartLine(1000L, 1), new CartLine(292L, 1));
	}
}
