package com.martpick.compare;

import java.util.List;

/**
 * 장바구니의 모든 상품 가격이 확인된 판매점.
 *
 * @param totalPrice 장바구니 전체 총가격 (모든 상품 소계의 합)
 */
public record CompleteStore(long entpId, String name, String address, List<ItemPrice> items, long totalPrice) {
}
