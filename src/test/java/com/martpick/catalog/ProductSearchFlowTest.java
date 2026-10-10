package com.martpick.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 PostgreSQL로 상품 검색 요청 전체를 확인한다. PostgreSQL은 문자열에 NUL(0x00)을 저장·비교할 수 없어,
 * 검색어에 NUL이 있으면 DB 조회에서 예외가 나 500이 되었다. 테스트 상품은 트랜잭션 롤백으로 남기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductSearchFlowTest {

	private static final long RICE_ID = 9_000_601L;
	private static final String RICE_NAME = "마트픽검색 햇반(210g)";
	private static final String INVALID_KEYWORD_MESSAGE = "검색어에 사용할 수 없는 문자가 포함되어 있습니다.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository productRepository;

	@BeforeEach
	void setUp() {
		productRepository.save(new Product(RICE_ID, RICE_NAME, "210", "G"));
		productRepository.flush();
	}

	@Test
	void 한글_검색어로_상품을_찾는다() throws Exception {
		mockMvc.perform(get("/products").param("keyword", "마트픽검색 햇반"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(RICE_NAME)))
				.andExpect(content().string(not(containsString(INVALID_KEYWORD_MESSAGE))));
	}

	@Test
	void 앞뒤_공백과_탭_개행은_제거하고_찾는다() throws Exception {
		mockMvc.perform(get("/products").param("keyword", " \t마트픽검색 햇반\n "))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(RICE_NAME)))
				.andExpect(content().string(not(containsString(INVALID_KEYWORD_MESSAGE))));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void 빈_검색어는_전체_상품을_보여_준다(String keyword) throws Exception {
		mockMvc.perform(get("/products").param("keyword", keyword))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(RICE_NAME)))
				.andExpect(content().string(not(containsString(INVALID_KEYWORD_MESSAGE))));
	}

	@ParameterizedTest
	@ValueSource(strings = {"\u0000", "마트픽\u0000검색", "\u0001", "\u007f", "\u0085", "햇반\n밥"})
	void 사용할_수_없는_문자가_있으면_500이_아니라_400과_안내를_보여_준다(String keyword) throws Exception {
		mockMvc.perform(get("/products").param("keyword", keyword))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString(INVALID_KEYWORD_MESSAGE)))
				.andExpect(content().string(containsString(RICE_NAME)));
	}
}
