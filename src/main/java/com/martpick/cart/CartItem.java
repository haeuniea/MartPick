package com.martpick.cart;

import com.martpick.catalog.Product;
import com.martpick.member.Member;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id")
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "good_id")
	private Product product;

	private int quantity;

	public CartItem(Member member, Product product, int quantity) {
		this.member = member;
		this.product = product;
		this.quantity = quantity;
	}

	/**
	 * 수량을 늘린다. 결과가 상한을 넘으면 조정하지 않고 거부한다. 비회원 장바구니와 같은 규칙과 메시지를 쓴다.
	 */
	public void addQuantity(int amount) {
		if (amount > GuestCart.MAX_QUANTITY - quantity) {
			throw new InvalidCartRequestException("상품당 최대 " + GuestCart.MAX_QUANTITY + "개까지 담을 수 있습니다.");
		}
		quantity += amount;
	}

	public void changeQuantity(int quantity) {
		requireValidQuantity(quantity);
		this.quantity = quantity;
	}

	static void requireValidQuantity(int quantity) {
		if (quantity < GuestCart.MIN_QUANTITY || quantity > GuestCart.MAX_QUANTITY) {
			throw new InvalidCartRequestException(GuestCart.QUANTITY_RANGE_MESSAGE);
		}
	}
}
