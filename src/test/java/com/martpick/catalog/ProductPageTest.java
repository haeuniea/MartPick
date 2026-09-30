package com.martpick.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProductPageTest {

	@Test
	void 현재_페이지_앞뒤_2페이지까지_페이지_번호를_보여_준다() {
		ProductPage middle = page(5, 10);

		assertThat(middle.startPage()).isEqualTo(3);
		assertThat(middle.endPage()).isEqualTo(7);
	}

	@Test
	void 처음과_끝에서는_페이지_번호_범위를_전체_페이지_안으로_제한한다() {
		assertThat(page(1, 10).startPage()).isEqualTo(1);
		assertThat(page(1, 10).endPage()).isEqualTo(3);
		assertThat(page(10, 10).startPage()).isEqualTo(8);
		assertThat(page(10, 10).endPage()).isEqualTo(10);
		assertThat(page(1, 2).endPage()).isEqualTo(2);
	}

	@Test
	void 이전_다음_페이지_여부를_알려_준다() {
		assertThat(page(1, 3).hasPrevious()).isFalse();
		assertThat(page(1, 3).hasNext()).isTrue();
		assertThat(page(3, 3).hasPrevious()).isTrue();
		assertThat(page(3, 3).hasNext()).isFalse();
	}

	@Test
	void 결과가_없으면_이전_다음_페이지가_없다() {
		ProductPage empty = new ProductPage(List.of(), 1, 0, 0);

		assertThat(empty.hasPrevious()).isFalse();
		assertThat(empty.hasNext()).isFalse();
	}

	private static ProductPage page(int page, int totalPages) {
		return new ProductPage(List.of(), page, totalPages, totalPages * 30L);
	}
}
