package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.martpick.compare.CartLine;
import com.martpick.member.MemberUserDetails;

/**
 * 디스패처는 회원 여부에 따라 한쪽 서비스에만 위임하고, 다른 쪽은 건드리지 않아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

	private static final MemberUserDetails MEMBER = new MemberUserDetails(7L, "tester01", "hash");

	@Mock
	private GuestCartService guestCartService;

	@Mock
	private GuestCart guestCart;

	@Mock
	private MemberCartService memberCartService;

	@InjectMocks
	private CartService cartService;

	@Test
	void 비회원_담기는_세션_장바구니에만_위임한다() {
		given(guestCartService.add(1000L)).willReturn("팔도 왕뚜껑(110g)");

		assertThat(cartService.add(null, 1000L)).isEqualTo("팔도 왕뚜껑(110g)");

		verifyNoInteractions(memberCartService);
	}

	@Test
	void 회원_담기는_회원_ID로_DB_장바구니에만_위임한다() {
		given(memberCartService.add(7L, 1000L)).willReturn("팔도 왕뚜껑(110g)");

		assertThat(cartService.add(MEMBER, 1000L)).isEqualTo("팔도 왕뚜껑(110g)");

		verifyNoInteractions(guestCartService);
	}

	@Test
	void 비회원_수량_변경은_세션_장바구니에만_위임한다() {
		cartService.changeQuantity(null, 1000L, 5);

		then(guestCartService).should().changeQuantity(1000L, 5);
		verifyNoInteractions(memberCartService);
	}

	@Test
	void 회원_수량_변경은_회원_ID로_DB_장바구니에만_위임한다() {
		cartService.changeQuantity(MEMBER, 1000L, 5);

		then(memberCartService).should().changeQuantity(7L, 1000L, 5);
		verifyNoInteractions(guestCartService);
	}

	@Test
	void 비회원_삭제는_세션_장바구니에만_위임한다() {
		cartService.remove(null, 1000L);

		then(guestCartService).should().remove(1000L);
		verifyNoInteractions(memberCartService);
	}

	@Test
	void 회원_삭제는_회원_ID로_DB_장바구니에만_위임한다() {
		cartService.remove(MEMBER, 1000L);

		then(memberCartService).should().remove(7L, 1000L);
		verifyNoInteractions(guestCartService);
	}

	@Test
	void 비회원_조회는_세션_장바구니만_읽는다() {
		List<CartItemView> items = List.of(new CartItemView(1000L, "팔도 왕뚜껑(110g)", "110 G", 2));
		given(guestCartService.items()).willReturn(items);

		assertThat(cartService.items(null)).isSameAs(items);

		verifyNoInteractions(memberCartService);
	}

	@Test
	void 회원_조회는_회원_ID로_DB_장바구니만_읽는다() {
		List<CartItemView> items = List.of(new CartItemView(1000L, "팔도 왕뚜껑(110g)", "110 G", 2));
		given(memberCartService.items(7L)).willReturn(items);

		assertThat(cartService.items(MEMBER)).isSameAs(items);

		verifyNoInteractions(guestCartService, guestCart);
	}

	@Test
	void 비회원_비교_입력은_세션_장바구니에서만_만든다() {
		List<CartLine> lines = List.of(new CartLine(1000L, 2));
		given(guestCart.lines()).willReturn(lines);

		assertThat(cartService.lines(null)).isSameAs(lines);

		verifyNoInteractions(memberCartService, guestCartService);
	}

	@Test
	void 회원_비교_입력은_회원_ID로_DB_장바구니에서만_만든다() {
		List<CartLine> lines = List.of(new CartLine(1000L, 2));
		given(memberCartService.lines(7L)).willReturn(lines);

		assertThat(cartService.lines(MEMBER)).isSameAs(lines);

		verifyNoInteractions(guestCartService, guestCart);
	}
}
