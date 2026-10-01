package com.martpick.config;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 로그인 후 복귀할 화면(`continue` 파라미터)을 검증한다 (PROJECT_SPEC 2-4).
 * 허용 목록에 있는 GET 화면 경로만 통과시키고, 유지할 쿼리 이름도 화면별로 제한한다.
 * 검증을 통과한 값은 서비스 내부 경로로 새로 조립하므로 외부 URL·프로토콜 상대 URL·개행 등이 섞일 수 없다.
 */
public final class ContinueUrl {

	private static final Map<String, Set<String>> ALLOWED = Map.of(
			"/products", Set.of("keyword", "page"),
			"/cart", Set.of(),
			"/compare", Set.of("areaCode"));

	private static final int MAX_VALUE_LENGTH = 200;
	private static final int MAX_PARAMS = 5;

	private ContinueUrl() {
	}

	/**
	 * @return 안전한 내부 경로(쿼리 포함). 허용되지 않거나 형식이 잘못되면 {@code null}
	 */
	public static String resolve(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		int queryStart = raw.indexOf('?');
		String path = queryStart < 0 ? raw : raw.substring(0, queryStart);
		Set<String> allowedNames = ALLOWED.get(path);
		if (allowedNames == null) {
			return null;
		}
		if (queryStart < 0) {
			return path;
		}
		String rawQuery = raw.substring(queryStart + 1);
		if (rawQuery.chars().anyMatch(c -> Character.isISOControl(c) || c == '#' || c == '\\')) {
			return null;
		}
		String query = buildQuery(rawQuery, allowedNames);
		if (query == null) {
			return null;
		}
		return query.isEmpty() ? path : path + "?" + query;
	}

	private static String buildQuery(String rawQuery, Set<String> allowedNames) {
		List<String> pairs = new ArrayList<>();
		for (String pair : rawQuery.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int equals = pair.indexOf('=');
			String name = equals < 0 ? pair : pair.substring(0, equals);
			if (!allowedNames.contains(name)) {
				continue;
			}
			String value = decode(equals < 0 ? "" : pair.substring(equals + 1));
			if (value == null || value.length() > MAX_VALUE_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
				return null;
			}
			pairs.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
		}
		return pairs.size() > MAX_PARAMS ? null : String.join("&", pairs);
	}

	private static String decode(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
