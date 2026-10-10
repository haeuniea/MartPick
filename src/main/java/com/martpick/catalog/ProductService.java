package com.martpick.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 수집된 전체 상품을 검색한다 (PROJECT_SPEC 2-1). 검색어가 비어 있으면 전체 상품을 반환한다.
 * 결과는 상품 ID 순으로 30개씩 나누며, 범위를 벗어난 페이지 번호는 첫 페이지 또는 마지막 페이지로 보정한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	static final int PAGE_SIZE = 30;

	private final ProductRepository productRepository;

	/**
	 * @param page 1부터 시작하는 페이지 번호
	 * @throws InvalidSearchKeywordException 앞뒤 공백을 제거한 검색어에 제어 문자가 있는 경우. DB를 조회하기 전에 거부한다.
	 */
	public ProductPage search(String keyword, int page) {
		String normalized = StringUtils.hasText(keyword) ? keyword.strip() : null;
		if (normalized != null && normalized.chars().anyMatch(Character::isISOControl)) {
			throw new InvalidSearchKeywordException();
		}
		// 조회 offset이 int 범위를 넘지 않도록 상한을 둔다. 실제 페이지 수를 넘으면 아래에서 마지막 페이지로 보정한다.
		int requested = Math.min(Math.max(page, 1), Integer.MAX_VALUE / PAGE_SIZE);
		Page<Product> result = find(normalized, requested);
		if (result.getTotalPages() > 0 && requested > result.getTotalPages()) {
			requested = result.getTotalPages();
			result = find(normalized, requested);
		}
		if (result.getTotalPages() == 0) {
			requested = 1;
		}
		return new ProductPage(result.getContent().stream().map(ProductView::from).toList(),
				requested, result.getTotalPages(), result.getTotalElements());
	}

	private Page<Product> find(String keyword, int page) {
		PageRequest pageRequest = PageRequest.of(page - 1, PAGE_SIZE, Sort.by("goodId"));
		return keyword == null
				? productRepository.findAll(pageRequest)
				: productRepository.findByNameContainingIgnoreCase(keyword, pageRequest);
	}
}
