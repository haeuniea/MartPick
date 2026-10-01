package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CartItemTest {

	@Test
	void 수량을_더하면_늘어난다() {
		CartItem item = new CartItem(null, null, 1);

		item.addQuantity(1);

		assertThat(item.getQuantity()).isEqualTo(2);
	}

	@Test
	void 더한_결과가_99이면_허용한다() {
		CartItem item = new CartItem(null, null, 98);

		item.addQuantity(1);

		assertThat(item.getQuantity()).isEqualTo(99);
	}

	@Test
	void 더한_결과가_99를_넘으면_조정하지_않고_거부한다() {
		CartItem item = new CartItem(null, null, 99);

		assertThatThrownBy(() -> item.addQuantity(1))
				.isInstanceOf(InvalidCartRequestException.class)
				.hasMessage("상품당 최대 99개까지 담을 수 있습니다.");
		assertThat(item.getQuantity()).isEqualTo(99);
	}

	@Test
	void 거부_메시지는_비회원_장바구니와_같다() {
		GuestCart guest = new GuestCart();
		for (int i = 0; i < 99; i++) {
			guest.add(1L);
		}
		String guestMessage = catchMessage(() -> guest.add(1L));
		CartItem item = new CartItem(null, null, 99);

		assertThat(catchMessage(() -> item.addQuantity(1))).isEqualTo(guestMessage);
	}

	@Test
	void 수량_변경은_1에서_99까지_허용한다() {
		CartItem item = new CartItem(null, null, 5);

		item.changeQuantity(1);
		assertThat(item.getQuantity()).isEqualTo(1);
		item.changeQuantity(99);
		assertThat(item.getQuantity()).isEqualTo(99);
	}

	@Test
	void 범위를_벗어난_수량_변경은_거부하고_기존_수량을_유지한다() {
		CartItem item = new CartItem(null, null, 5);

		for (int invalid : new int[] {0, -1, 100, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
			assertThatThrownBy(() -> item.changeQuantity(invalid))
					.isInstanceOf(InvalidCartRequestException.class)
					.hasMessage(GuestCart.QUANTITY_RANGE_MESSAGE);
		}
		assertThat(item.getQuantity()).isEqualTo(5);
	}

	@Test
	void 더하는_값이_매우_커도_정수_넘침_없이_거부한다() {
		CartItem item = new CartItem(null, null, 99);

		assertThatThrownBy(() -> item.addQuantity(Integer.MAX_VALUE)).isInstanceOf(InvalidCartRequestException.class);
		assertThat(item.getQuantity()).isEqualTo(99);
	}

	private static String catchMessage(Runnable action) {
		try {
			action.run();
		} catch (InvalidCartRequestException e) {
			return e.getMessage();
		}
		throw new AssertionError("InvalidCartRequestException이 발생하지 않았습니다.");
	}
}
