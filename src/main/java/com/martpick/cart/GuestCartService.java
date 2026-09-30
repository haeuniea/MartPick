package com.martpick.cart;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.catalog.ProductView;
import com.martpick.compare.CartLine;

import lombok.RequiredArgsConstructor;

/**
 * 비회원 장바구니 조작과 화면 표시용 조회. 수집된 상품만 담을 수 있다.
 * {@link GuestCart}는 세션 범위 프록시로 주입되어 요청한 사용자의 세션 장바구니를 가리킨다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestCartService {

	private final GuestCart guestCart;
	private final ProductRepository productRepository;

	/**
	 * @return 담은 상품명
	 */
	public String add(long goodId) {
		Product product = productRepository.findById(goodId)
				.orElseThrow(() -> new InvalidCartRequestException("담을 수 없는 상품입니다."));
		guestCart.add(goodId);
		return product.getName();
	}

	public void changeQuantity(long goodId, int quantity) {
		guestCart.changeQuantity(goodId, quantity);
	}

	public void remove(long goodId) {
		guestCart.remove(goodId);
	}

	/**
	 * 담은 순서대로 상품명·규격·수량을 반환한다.
	 */
	public List<CartItemView> items() {
		List<CartLine> lines = guestCart.lines();
		if (lines.isEmpty()) {
			return List.of();
		}
		Map<Long, ProductView> products = productRepository.findAllById(lines.stream().map(CartLine::goodId).toList())
				.stream()
				.map(ProductView::from)
				.collect(Collectors.toMap(ProductView::goodId, Function.identity()));
		return lines.stream()
				.map(line -> {
					ProductView product = products.get(line.goodId());
					return product == null ? null
							: new CartItemView(line.goodId(), product.name(), product.spec(), line.quantity());
				})
				.filter(Objects::nonNull)
				.toList();
	}
}
