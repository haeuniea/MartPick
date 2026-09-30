package com.martpick.catalog;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 수집된 상품만 검색한다 (PROJECT_SPEC 2-1). 검색어가 비어 있으면 전체 상품을 반환한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private final ProductRepository productRepository;

	public List<ProductView> search(String keyword) {
		List<Product> products = StringUtils.hasText(keyword)
				? productRepository.findByNameContainingIgnoreCaseOrderByGoodIdAsc(keyword.strip())
				: productRepository.findAll(Sort.by("goodId"));
		return products.stream().map(ProductView::from).toList();
	}
}
