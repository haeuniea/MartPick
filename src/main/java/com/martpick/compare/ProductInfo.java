package com.martpick.compare;

/**
 * 상품명과 규격(API 원문). 규격 표기 형식은 화면에서 정한다.
 */
public record ProductInfo(long goodId, String name, String totalCnt, String totalDivCode) {
}
