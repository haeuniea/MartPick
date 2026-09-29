package com.martpick.compare;

import java.util.List;

/**
 * 장바구니 상품 중 일부 상품의 가격만 확인된 판매점. 장바구니 전체 총가격은 두지 않는다.
 *
 * @param missingProducts 기준 조사일 가격이 확인되지 않은 상품 (품절·미판매를 뜻하지 않음)
 * @param knownSubtotal 가격이 확인된 상품 소계의 합. 장바구니 전체 총가격이 아니다.
 */
public record PartialStore(long entpId, String name, String address, List<ItemPrice> pricedItems,
		List<ProductInfo> missingProducts, long knownSubtotal) {
}
