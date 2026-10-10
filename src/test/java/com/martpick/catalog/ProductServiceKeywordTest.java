package com.martpick.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * 검색어 검증은 DB 조회 전에 끝나야 한다. 저장소를 대체해 잘못된 검색어에서 DB 조회가 한 번도 일어나지 않는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceKeywordTest {

	@Mock
	private ProductRepository productRepository;

	@InjectMocks
	private ProductService productService;

	@ParameterizedTest
	@ValueSource(strings = {"\u0000", "마트픽\u0000검색", "\u0000마트픽", "마트픽\u0000", "\u0001", "\u0002", "\u0008", "\u001b", "\u007f", "\u0080",
			"\u0085", "\u009f", "햇반\n밥", "햇반\t밥", "햇반\r밥"})
	void 제어_문자가_있는_검색어는_DB를_조회하지_않고_거부한다(String keyword) {
		assertThatThrownBy(() -> productService.search(keyword, 1))
				.isInstanceOf(InvalidSearchKeywordException.class)
				.hasMessage("검색어에 사용할 수 없는 문자가 포함되어 있습니다.");

		verifyNoInteractions(productRepository);
	}

	@Test
	void 앞뒤의_공백_탭_개행은_제거한_뒤_검색한다() {
		given(productRepository.findByNameContainingIgnoreCase(eq("햇반"), any(Pageable.class))).willReturn(emptyPage());

		productService.search(" \t\n햇반\r\n ", 1);

		then(productRepository).should().findByNameContainingIgnoreCase(eq("햇반"), any(Pageable.class));
	}

	@Test
	void 검색어_중간의_공백은_그대로_유지한다() {
		given(productRepository.findByNameContainingIgnoreCase(eq("햇반 210g"), any(Pageable.class))).willReturn(emptyPage());

		productService.search("햇반 210g", 1);

		then(productRepository).should().findByNameContainingIgnoreCase(eq("햇반 210g"), any(Pageable.class));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", " ", "   ", "\t", "\n", " \t\n ", "\u001f"})
	void 검색어가_없거나_공백뿐이면_전체_상품을_조회한다(String keyword) {
		given(productRepository.findAll(any(Pageable.class))).willReturn(emptyPage());

		assertThat(productService.search(keyword, 1).products()).isEmpty();

		then(productRepository).should().findAll(any(Pageable.class));
		then(productRepository).shouldHaveNoMoreInteractions();
	}

	@ParameterizedTest
	@ValueSource(strings = {"햇반", "Hat Ban", "진라면 순한맛(5개입)", "%", "_", "100%", "a_b", "\\", "'; drop table product; --", "😀"})
	void 한글_영문_기호_이모지가_섞인_일반_검색어는_거부하지_않는다(String keyword) {
		given(productRepository.findByNameContainingIgnoreCase(eq(keyword), any(Pageable.class))).willReturn(emptyPage());

		productService.search(keyword, 1);

		then(productRepository).should().findByNameContainingIgnoreCase(eq(keyword), any(Pageable.class));
	}

	private static Page<Product> emptyPage() {
		return new PageImpl<>(List.of());
	}
}
