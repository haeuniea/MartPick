package com.martpick.compare;

/**
 * @param subtotal 단가 × 수량
 */
public record ItemPrice(ProductInfo product, int unitPrice, int quantity, long subtotal) {
}
