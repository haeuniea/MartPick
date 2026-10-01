package com.martpick.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContinueUrlTest {

	@Test
	void 허용된_GET_화면_경로는_그대로_통과한다() {
		assertThat(ContinueUrl.resolve("/products")).isEqualTo("/products");
		assertThat(ContinueUrl.resolve("/cart")).isEqualTo("/cart");
		assertThat(ContinueUrl.resolve("/compare")).isEqualTo("/compare");
	}

	@Test
	void 상품_탐색은_검색어와_페이지_번호를_유지한다() {
		assertThat(ContinueUrl.resolve("/products?keyword=%ED%96%87%EB%B0%98&page=2"))
				.isEqualTo("/products?keyword=%ED%96%87%EB%B0%98&page=2");
	}

	@Test
	void 가격_비교는_지역_코드만_유지한다() {
		assertThat(ContinueUrl.resolve("/compare?areaCode=020100000")).isEqualTo("/compare?areaCode=020100000");
		assertThat(ContinueUrl.resolve("/compare?areaCode=020100000&keyword=x")).isEqualTo("/compare?areaCode=020100000");
	}

	@Test
	void 장바구니는_쿼리를_유지하지_않는다() {
		assertThat(ContinueUrl.resolve("/cart?page=2&x=1")).isEqualTo("/cart");
	}

	@Test
	void 허용하지_않은_쿼리_이름은_버린다() {
		assertThat(ContinueUrl.resolve("/products?evil=1&page=2")).isEqualTo("/products?page=2");
		assertThat(ContinueUrl.resolve("/products?")).isEqualTo("/products");
	}

	@Test
	void 검색어의_특수문자는_다시_인코딩해_의미를_보존한다() {
		assertThat(ContinueUrl.resolve("/products?keyword=1%2B1")).isEqualTo("/products?keyword=1%2B1");
		assertThat(ContinueUrl.resolve("/products?keyword=a+b")).isEqualTo("/products?keyword=a+b");
		assertThat(ContinueUrl.resolve("/products?keyword=A%26B%23C")).isEqualTo("/products?keyword=A%26B%23C");
	}

	@Test
	void 공백은_인코딩된_형태와_그대로인_형태_모두_같은_표현으로_유지한다() {
		assertThat(ContinueUrl.resolve("/products?keyword=%ED%96%87%EB%B0%98%20%EC%9A%B0%EC%9C%A0"))
				.isEqualTo("/products?keyword=%ED%96%87%EB%B0%98+%EC%9A%B0%EC%9C%A0");
		assertThat(ContinueUrl.resolve("/products?keyword=%ED%96%87%EB%B0%98+%EC%9A%B0%EC%9C%A0"))
				.isEqualTo("/products?keyword=%ED%96%87%EB%B0%98+%EC%9A%B0%EC%9C%A0");
		assertThat(ContinueUrl.resolve("/products?keyword=a b")).isEqualTo("/products?keyword=a+b");
	}

	@Test
	void 퍼센트는_한_번만_디코딩하고_한_번만_인코딩한다() {
		assertThat(ContinueUrl.resolve("/products?keyword=100%25")).isEqualTo("/products?keyword=100%25");
		assertThat(ContinueUrl.resolve("/products?keyword=%2525")).isEqualTo("/products?keyword=%2525");
	}

	@Test
	void 이미_검증된_값을_다시_통과시켜도_변하지_않는다() {
		for (String value : new String[] {
				"/products?keyword=A%26B+%231%2B1+100%25+%EC%9A%B0%EC%9C%A0&page=2", "/products?keyword=%2525",
				"/compare?areaCode=020100000", "/cart", "/products?keyword=%ED%96%87%EB%B0%98" }) {
			String once = ContinueUrl.resolve(value);
			assertThat(once).as(value).isNotNull();
			assertThat(ContinueUrl.resolve(once)).as(value).isEqualTo(once);
		}
	}

	@Test
	void 값이_없거나_비어_있으면_null이다() {
		assertThat(ContinueUrl.resolve(null)).isNull();
		assertThat(ContinueUrl.resolve("")).isNull();
		assertThat(ContinueUrl.resolve("   ")).isNull();
	}

	@Test
	void 외부_URL과_위험한_형식은_차단한다() {
		for (String value : new String[] {
				"https://evil.com", "http://evil.com/products", "//evil.com", "//evil.com/products", "/\\evil.com",
				"\\evil.com", "\\\\evil.com", "javascript:alert(1)", "data:text/html,x", "products", "evil.com",
				"/products/../admin", "/products/", "/products//", "/PRODUCTS", "/products;jsessionid=1",
				"/products#fragment", "/products?keyword=a#b", "/products\r\nLocation: https://evil.com",
				"/products%0d%0aLocation:%20x", "/products?keyword=%0d%0a", "/products\t", " /products", "/products ",
				"/products?keyword=%zz", "/%2e%2e/admin", "/products@evil.com", "http:/evil.com", "/\\/evil.com" }) {
			assertThat(ContinueUrl.resolve(value)).as(value).isNull();
		}
	}

	@Test
	void POST_전용_경로와_인증_경로와_그_외_경로로는_복귀하지_않는다() {
		for (String value : new String[] {
				"/cart/items", "/cart/items/1000/quantity", "/cart/items/1000/delete", "/login", "/signup", "/logout",
				"/admin", "/error", "/css/main.css", "/" }) {
			assertThat(ContinueUrl.resolve(value)).as(value).isNull();
		}
	}

	@Test
	void 너무_긴_값은_차단한다() {
		assertThat(ContinueUrl.resolve("/products?keyword=" + "a".repeat(201))).isNull();
		assertThat(ContinueUrl.resolve("/products?keyword=" + "a".repeat(200))).isNotNull();
	}
}
