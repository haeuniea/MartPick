package com.martpick.compare;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductInfoTest {

	@Test
	void 규격은_수치와_단위_코드_원문으로_표시한다() {
		assertThat(new ProductInfo(993L, "오리온 초코파이(18개입)", "702", "G").spec()).isEqualTo("702 G");
	}

	@Test
	void 규격_값이_없으면_규격을_표시하지_않는다() {
		assertThat(new ProductInfo(934L, "애호박", null, "EA").spec()).isNull();
		assertThat(new ProductInfo(934L, "애호박", "1", " ").spec()).isNull();
	}
}
