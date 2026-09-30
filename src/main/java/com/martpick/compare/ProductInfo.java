package com.martpick.compare;

import org.springframework.util.StringUtils;

/**
 * 상품명과 규격(API 원문).
 */
public record ProductInfo(long goodId, String name, String totalCnt, String totalDivCode) {

	/**
	 * 화면에 따로 표시할 규격 (예: "702 G", PROJECT_SPEC Q24). 규격 값이 없으면 {@code null}.
	 */
	public String spec() {
		if (!StringUtils.hasText(totalCnt) || !StringUtils.hasText(totalDivCode)) {
			return null;
		}
		return totalCnt.strip() + " " + totalDivCode.strip();
	}
}
