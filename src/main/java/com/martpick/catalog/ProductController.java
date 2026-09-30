package com.martpick.catalog;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.martpick.config.MartPickProperties;

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

	@GetMapping("/products")
	public String products(@RequestParam(required = false) String keyword, Model model) {
		model.addAttribute("products", productService.search(keyword));
		model.addAttribute("keyword", keyword == null ? "" : keyword.strip());
		model.addAttribute("inspectDay", properties.inspectDay());
		return "products";
	}
}
