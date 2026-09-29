package com.martpick.catalog;

/**
 * 가격 비교용 조회 행. 판매점 정보와 한 상품의 가격을 담는다.
 */
public record StorePriceRow(
		long entpId,
		String storeName,
		String roadAddrBasic,
		String roadAddrDetail,
		String plmkAddrBasic,
		String plmkAddrDetail,
		long goodId,
		int price) {
}
