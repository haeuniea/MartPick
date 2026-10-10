package com.martpick.catalog;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.martpick.config.MartPickProperties;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ProductController {

	private final ProductService productService;
	private final MartPickProperties properties;

	@GetMapping("/")
	public String home() {
		return "redirect:/products";
	}

	/**
	 * 페이지 번호가 음수·0·숫자가 아니면 첫 페이지를 요청한다 (PROJECT_SPEC 2-1).
	 * 검색어에 사용할 수 없는 문자가 있으면 400으로 안내하고, 그 검색어는 다시 쓰지 않고 전체 상품을 보여 준다.
	 */
	@GetMapping("/products")
	public String products(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String page, Model model, HttpServletResponse response) {
		int pageNumber = parsePage(page);
		try {
			model.addAttribute("result", productService.search(keyword, pageNumber));
			model.addAttribute("keyword", keyword == null ? "" : keyword.strip());
		} catch (InvalidSearchKeywordException e) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			model.addAttribute("error", e.getMessage());
			model.addAttribute("result", productService.search(null, pageNumber));
			model.addAttribute("keyword", "");
		}
		model.addAttribute("inspectDay", properties.inspectDay());
		return "products";
	}

	private static int parsePage(String page) {
		if (page == null || !page.matches("\\d+")) {
			return 1;
		}
		long value = page.length() > 10 ? Long.MAX_VALUE : Long.parseLong(page);
		return (int) Math.max(1, Math.min(value, Integer.MAX_VALUE));
	}
}
