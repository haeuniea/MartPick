package com.martpick.compare;

/**
 * 비교할 장바구니 한 줄. 세션·회원 장바구니 모두 이 형태로 변환해 전달한다.
 */
public record CartLine(long goodId, int quantity) {
}
