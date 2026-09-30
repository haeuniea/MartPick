package com.martpick.catalog;

import java.util.List;

/**
 * 상품 탐색 화면의 한 페이지.
 *
 * @param page 1부터 시작하는 현재 페이지. 결과가 없으면 1
 * @param totalPages 전체 페이지 수. 결과가 없으면 0
 */
public record ProductPage(List<ProductView> products, int page, int totalPages, long totalCount) {

	/** 페이지 번호 목록에 현재 페이지 앞뒤로 보여 줄 페이지 수 */
	private static final int PAGE_WINDOW = 2;

	public boolean hasPrevious() {
		return page > 1;
	}

	public boolean hasNext() {
		return page < totalPages;
	}

	public int startPage() {
		return Math.max(1, page - PAGE_WINDOW);
	}

	public int endPage() {
		return Math.min(totalPages, page + PAGE_WINDOW);
	}
}
