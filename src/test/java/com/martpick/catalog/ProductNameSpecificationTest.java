package com.martpick.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductNameSpecificationTest {

	@Test
	void 공백으로_나누고_소문자로_바꾸며_중복_토큰은_한_번만_남긴다() {
		assertThat(ProductNameSpecification.tokens("서울우유 흰우유")).containsExactly("서울우유", "흰우유");
		assertThat(ProductNameSpecification.tokens("  Hat   BAN　hat ban ")).containsExactly("hat", "ban");
		assertThat(ProductNameSpecification.tokens("우유 1L 우유 1l")).containsExactly("우유", "1l");
	}

	@Test
	void 공백이_없으면_토큰_하나이고_와일드카드_문자도_그대로_둔다() {
		assertThat(ProductNameSpecification.tokens("진라면순한맛")).containsExactly("진라면순한맛");
		assertThat(ProductNameSpecification.tokens("100%_a\b")).containsExactly("100%_a\b");
	}
}
