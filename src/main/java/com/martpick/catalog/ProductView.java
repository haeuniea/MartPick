package com.martpick.catalog;

import org.springframework.util.StringUtils;

/**
 * 상품 탐색·장바구니 화면용 상품 정보. 상품명과 규격은 상품 API 원문을 그대로 사용한다 (PROJECT_SPEC 2-1).
 *
 * @param spec 규격 수치와 단위 코드 원문 (예: "702 G"). 규격 값이 없으면 {@code null}
 */
public record ProductView(long goodId, String name, String spec) {

	public static ProductView from(Product product) {
		return new ProductView(product.getGoodId(), product.getName(), spec(product));
	}

	private static String spec(Product product) {
		if (!StringUtils.hasText(product.getTotalCnt()) || !StringUtils.hasText(product.getTotalDivCode())) {
			return null;
		}
		return product.getTotalCnt().strip() + " " + product.getTotalDivCode().strip();
	}
}
