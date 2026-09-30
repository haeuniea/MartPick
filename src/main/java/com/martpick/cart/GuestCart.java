package com.martpick.cart;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.martpick.compare.CartLine;

/**
 * 비회원 장바구니. 사용자 세션(HttpSession)마다 하나씩 만들어지며 상품 ID와 수량만 담는다 (PROJECT_SPEC 2-2).
 * 같은 세션의 동시 요청을 고려해 메서드를 동기화한다.
 */
@Component
@SessionScope
public class GuestCart implements Serializable {

	private static final long serialVersionUID = 1L;

	static final int MIN_QUANTITY = 1;
	static final int MAX_QUANTITY = 99;
	static final String QUANTITY_RANGE_MESSAGE = "수량은 1~99 사이의 정수로 입력해 주세요.";

	private final LinkedHashMap<Long, Integer> quantities = new LinkedHashMap<>();

	/**
	 * 상품을 1개 담는다. 이미 담긴 상품이면 1개 늘리고, 99개를 넘으면 조정하지 않고 거부한다.
	 */
	public synchronized void add(long goodId) {
		int next = quantities.getOrDefault(goodId, 0) + 1;
		if (next > MAX_QUANTITY) {
			throw new InvalidCartRequestException("상품당 최대 " + MAX_QUANTITY + "개까지 담을 수 있습니다.");
		}
		quantities.put(goodId, next);
	}

	/**
	 * 담긴 상품의 수량을 바꾼다. 담기지 않은 상품이면 아무것도 하지 않는다.
	 */
	public synchronized void changeQuantity(long goodId, int quantity) {
		if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
			throw new InvalidCartRequestException(QUANTITY_RANGE_MESSAGE);
		}
		quantities.computeIfPresent(goodId, (id, current) -> quantity);
	}

	public synchronized void remove(long goodId) {
		quantities.remove(goodId);
	}

	public synchronized boolean isEmpty() {
		return quantities.isEmpty();
	}

	/**
	 * 담은 순서대로 상품 ID와 수량을 반환한다.
	 */
	public synchronized List<CartLine> lines() {
		return quantities.entrySet().stream()
				.map(entry -> new CartLine(entry.getKey(), entry.getValue()))
				.toList();
	}
}
