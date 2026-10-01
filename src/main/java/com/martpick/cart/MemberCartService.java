package com.martpick.cart;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.catalog.ProductView;
import com.martpick.compare.CartLine;
import com.martpick.member.Member;
import com.martpick.member.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 장바구니(DB) 조작과 조회. 수량 규칙과 오류 메시지는 비회원 장바구니({@link GuestCartService})와 같다.
 *
 * <p>회원 ID는 호출하는 쪽(5-4에서 인증 주체)이 넘기며, 모든 조회·변경·삭제는 그 회원 ID로 범위를 제한한다.
 * 같은 회원의 장바구니를 바꾸는 작업(담기·수량 변경·삭제)은 같은 트랜잭션 안에서 먼저 회원 행에 쓰기 잠금을 걸어 직렬화한다.
 * 그래서 같은 상품을 동시에 담아도 중복 행이 생기지 않고, 수량 갱신이 사라지거나 99를 넘지 않는다.
 * 잠금은 그 회원 한 행뿐이므로 다른 회원의 작업과 조회는 막지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberCartService {

	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final CartItemRepository cartItemRepository;

	/**
	 * @return 담은 상품명
	 */
	@Transactional
	public String add(long memberId, long goodId) {
		Member member = lockMember(memberId);
		Product product = productRepository.findById(goodId)
				.orElseThrow(() -> new InvalidCartRequestException("담을 수 없는 상품입니다."));
		cartItemRepository.findByMemberIdAndProductGoodId(memberId, goodId).ifPresentOrElse(
				item -> item.addQuantity(1),
				() -> cartItemRepository.save(new CartItem(member, product, 1)));
		return product.getName();
	}

	/**
	 * 담긴 상품의 수량을 바꾼다. 담기지 않은 상품이면 아무것도 하지 않는다.
	 */
	@Transactional
	public void changeQuantity(long memberId, long goodId, int quantity) {
		CartItem.requireValidQuantity(quantity);
		lockMember(memberId);
		cartItemRepository.findByMemberIdAndProductGoodId(memberId, goodId)
				.ifPresent(item -> item.changeQuantity(quantity));
	}

	@Transactional
	public void remove(long memberId, long goodId) {
		lockMember(memberId);
		cartItemRepository.deleteByMemberIdAndProductGoodId(memberId, goodId);
	}

	/**
	 * 담은 순서대로 상품명·규격·수량을 반환한다.
	 */
	public List<CartItemView> items(long memberId) {
		return cartItemRepository.findByMemberIdOrderByIdAsc(memberId).stream()
				.map(item -> {
					ProductView product = ProductView.from(item.getProduct());
					return new CartItemView(product.goodId(), product.name(), product.spec(), item.getQuantity());
				})
				.toList();
	}

	/**
	 * 가격 비교({@code ComparisonService})에 넘길 입력을 담은 순서대로 만든다.
	 */
	public List<CartLine> lines(long memberId) {
		return cartItemRepository.findByMemberIdOrderByIdAsc(memberId).stream()
				.map(item -> new CartLine(item.getProduct().getGoodId(), item.getQuantity()))
				.toList();
	}

	private Member lockMember(long memberId) {
		return memberRepository.findWithLockById(memberId)
				.orElseThrow(() -> new InvalidCartRequestException("회원을 찾을 수 없습니다."));
	}
}
