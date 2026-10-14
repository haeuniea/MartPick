package com.martpick.catalog;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * 검색어를 공백 기준 토큰으로 나눠, 공백을 뺀 상품명에 모든 토큰이 들어 있는 상품을 찾는 조건이다 (PROJECT_SPEC 2-1).
 * 토큰은 LIKE 패턴의 와일드카드를 이스케이프한 바인딩 파라미터로만 전달하며 SQL에 이어 붙이지 않는다.
 */
final class ProductNameSpecification {

	private static final Pattern SEPARATORS = Pattern.compile("[\\s\\p{Z}]+");
	private static final char ESCAPE = '\\';

	private ProductNameSpecification() {
	}

	/** 검색어를 소문자 토큰으로 나눈다. 같은 토큰은 한 번만 남기고 순서는 입력 순서를 따른다. */
	static List<String> tokens(String keyword) {
		return SEPARATORS.splitAsStream(keyword.toLowerCase(Locale.ROOT))
				.filter(token -> !token.isEmpty())
				.distinct()
				.toList();
	}

	static Specification<Product> nameContainsAll(List<String> tokens) {
		return (root, query, cb) -> {
			Expression<String> compactName = cb.function("replace", String.class,
					cb.lower(root.get("name")), cb.literal(" "), cb.literal(""));
			Predicate[] predicates = tokens.stream()
					.map(token -> cb.like(compactName, "%" + escapeLike(token) + "%", ESCAPE))
					.toArray(Predicate[]::new);
			return cb.and(predicates);
		};
	}

	private static String escapeLike(String token) {
		return token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
