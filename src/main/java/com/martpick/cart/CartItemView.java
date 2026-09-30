package com.martpick.cart;

/**
 * 장바구니 화면용 상품 정보. 비교 전에는 가격을 표시하지 않으므로 가격 필드가 없다 (PROJECT_SPEC 2-2).
 *
 * @param spec 규격 원문. 규격 값이 없으면 {@code null}
 */
public record CartItemView(long goodId, String name, String spec, int quantity) {
}
