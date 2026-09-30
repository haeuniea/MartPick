package com.martpick.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductViewTest {

	@Test
	void 상품명은_원문_그대로_규격은_원문을_따로_표시한다() {
		ProductView view = ProductView.from(new Product(993L, "오리온 초코파이(18개입)", "702", "G"));

		assertThat(view.goodId()).isEqualTo(993L);
		assertThat(view.name()).isEqualTo("오리온 초코파이(18개입)");
		assertThat(view.spec()).isEqualTo("702 G");
	}

	@Test
	void 상품명에_규격이_있어도_규격을_따로_표시한다() {
		assertThat(ProductView.from(new Product(1000L, "팔도 왕뚜껑(110g)", "110", "G")).spec()).isEqualTo("110 G");
	}

	@Test
	void 규격_값이_없으면_규격을_표시하지_않는다() {
		assertThat(ProductView.from(new Product(934L, "애호박", null, "EA")).spec()).isNull();
		assertThat(ProductView.from(new Product(934L, "애호박", "1", " ")).spec()).isNull();
	}
}
