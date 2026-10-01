package com.martpick.cart;

import java.util.List;

import org.springframework.stereotype.Service;

import com.martpick.compare.CartLine;
import com.martpick.member.MemberUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 여부에 따라 장바구니 저장소를 고른다 (PROJECT_SPEC 2-2). 이 클래스는 분기만 하고 장바구니 규칙은 담지 않는다.
 *
 * <p>{@code member}가 {@code null}이면 비회원이므로 세션 장바구니({@link GuestCartService}, {@link GuestCart})를,
 * 아니면 그 회원의 DB 장바구니({@link MemberCartService})를 쓴다. 두 저장소는 서로 읽거나 합치지 않는다.
 * 회원은 요청 값이 아니라 컨트롤러가 인증 정보에서 받은 {@link MemberUserDetails}로만 정해진다.
 * 트랜잭션과 회원 행 잠금은 위임 대상 서비스가 각자 관리하므로 여기에는 {@code @Transactional}을 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CartService {

	private final GuestCartService guestCartService;
	private final GuestCart guestCart;
	private final MemberCartService memberCartService;

	/**
	 * @return 담은 상품명
	 */
	public String add(MemberUserDetails member, long goodId) {
		return member == null ? guestCartService.add(goodId) : memberCartService.add(member.getMemberId(), goodId);
	}

	public void changeQuantity(MemberUserDetails member, long goodId, int quantity) {
		if (member == null) {
			guestCartService.changeQuantity(goodId, quantity);
		} else {
			memberCartService.changeQuantity(member.getMemberId(), goodId, quantity);
		}
	}

	public void remove(MemberUserDetails member, long goodId) {
		if (member == null) {
			guestCartService.remove(goodId);
		} else {
			memberCartService.remove(member.getMemberId(), goodId);
		}
	}

	/**
	 * 담은 순서대로 상품명·규격·수량을 반환한다.
	 */
	public List<CartItemView> items(MemberUserDetails member) {
		return member == null ? guestCartService.items() : memberCartService.items(member.getMemberId());
	}

	/**
	 * 가격 비교({@code ComparisonService})에 넘길 입력을 담은 순서대로 반환한다.
	 */
	public List<CartLine> lines(MemberUserDetails member) {
		return member == null ? guestCart.lines() : memberCartService.lines(member.getMemberId());
	}
}
