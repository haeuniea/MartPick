package com.martpick.cart;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.martpick.compare.ComparisonService;
import com.martpick.config.MartPickProperties;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 비회원 장바구니 화면. 상태를 바꾸는 요청은 POST 처리 후 redirect한다 (PRG).
 */
@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

	private final GuestCartService guestCartService;
	private final ComparisonService comparisonService;
	private final MartPickProperties properties;

	@GetMapping
	public String cart(Model model) {
		List<CartItemView> items = guestCartService.items();
		model.addAttribute("items", items);
		model.addAttribute("regions", items.isEmpty() ? List.of() : comparisonService.comparableRegions());
		model.addAttribute("inspectDay", properties.inspectDay());
		return "cart";
	}

	/**
	 * 담기 후에도 상품 탐색 화면의 검색어와 페이지 번호를 유지한다 (PROJECT_SPEC 2-1).
	 * 페이지 번호는 1 이상의 정수일 때만 이어 붙이고, 범위 보정은 상품 탐색 화면이 맡는다.
	 * 목록에서 보던 위치로 돌아가도록 담은 상품의 카드 앵커로 이동하고, 그 카드에 결과를 표시하도록 상품 ID를 넘긴다.
	 */
	@PostMapping("/items")
	public String add(@RequestParam long goodId, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) String page, RedirectAttributes redirectAttributes) {
		try {
			String name = guestCartService.add(goodId);
			redirectAttributes.addFlashAttribute("message", name + "을(를) 장바구니에 담았습니다.");
		} catch (InvalidCartRequestException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		if (StringUtils.hasText(keyword)) {
			redirectAttributes.addAttribute("keyword", keyword.strip());
		}
		if (page != null && page.matches("[1-9]\\d{0,9}")) {
			redirectAttributes.addAttribute("page", page);
		}
		redirectAttributes.addFlashAttribute("lastGoodId", goodId);
		return "redirect:/products#product-" + goodId;
	}

	@PostMapping("/items/{goodId}/quantity")
	public String changeQuantity(@PathVariable long goodId, @Valid QuantityForm form, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", GuestCart.QUANTITY_RANGE_MESSAGE);
			return "redirect:/cart";
		}
		try {
			guestCartService.changeQuantity(goodId, form.quantity());
		} catch (InvalidCartRequestException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/cart";
	}

	@PostMapping("/items/{goodId}/delete")
	public String remove(@PathVariable long goodId) {
		guestCartService.remove(goodId);
		return "redirect:/cart";
	}
}
