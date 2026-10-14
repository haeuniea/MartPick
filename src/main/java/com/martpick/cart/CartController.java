package com.martpick.cart;

import java.util.List;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.martpick.member.MemberUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 장바구니 화면. 비회원은 세션 장바구니를, 로그인한 회원은 DB 장바구니를 쓴다 ({@link CartService}).
 * 회원은 인증 정보({@code @AuthenticationPrincipal})로만 정하며 요청 파라미터에서 받지 않는다.
 * 장바구니 상품 중 기준 조사일에 가격 데이터가 전혀 없는 상품은 화면에서 알려 주기만 하며, 상품을 빼거나 바꾸지 않는다.
 * 상태를 바꾸는 요청은 POST 처리 후 redirect한다 (PRG).
 */
@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

	/** 상품 탐색 화면의 검색창 maxlength와 같다. 이보다 긴 검색어는 이동 주소에 싣지 않는다. */
	static final int MAX_RETURN_KEYWORD_LENGTH = 50;

	private final CartService cartService;
	private final ComparisonService comparisonService;
	private final MartPickProperties properties;

	@GetMapping
	public String cart(@AuthenticationPrincipal MemberUserDetails member, Model model) {
		List<CartItemView> items = cartService.items(member);
		model.addAttribute("items", items);
		model.addAttribute("regions", items.isEmpty() ? List.of() : comparisonService.comparableRegions());
		model.addAttribute("noPriceGoodIds", items.isEmpty() ? Set.of()
				: comparisonService.goodIdsWithoutPrice(items.stream().map(CartItemView::goodId).toList()));
		model.addAttribute("inspectDay", properties.inspectDay());
		return "cart";
	}

	/**
	 * 담기 후에도 상품 탐색 화면의 검색어와 페이지 번호를 유지한다 (PROJECT_SPEC 2-1).
	 * 페이지 번호는 1 이상의 정수일 때만 이어 붙이고, 범위 보정은 상품 탐색 화면이 맡는다.
	 * 목록에서 보던 위치로 돌아가도록 담은 상품의 카드 앵커로 이동하고, 그 카드에 결과를 표시하도록 상품 ID를 넘긴다.
	 * 검색어는 돌아갈 화면을 꾸미는 용도일 뿐이므로, 너무 길거나 제어 문자가 있어 이동 주소를 해치거나 이동한 화면이 거부할 검색어는
	 * 이동 주소에 싣지 않는다. 담기는 그대로 처리하며, 이동 주소가 컨테이너의 헤더 크기 한도를 넘어 담은 뒤에 오류가 나는 일을 막는다.
	 */
	@PostMapping("/items")
	public String add(@AuthenticationPrincipal MemberUserDetails member, @RequestParam long goodId,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String page,
			RedirectAttributes redirectAttributes) {
		try {
			String name = cartService.add(member, goodId);
			redirectAttributes.addFlashAttribute("message", name + "을(를) 장바구니에 담았습니다.");
		} catch (InvalidCartRequestException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		if (StringUtils.hasText(keyword) && isReturnable(keyword.strip())) {
			redirectAttributes.addAttribute("keyword", keyword.strip());
		}
		if (page != null && page.matches("[1-9]\\d{0,9}")) {
			redirectAttributes.addAttribute("page", page);
		}
		redirectAttributes.addFlashAttribute("lastGoodId", goodId);
		return "redirect:/products#product-" + goodId;
	}

	private static boolean isReturnable(String keyword) {
		return keyword.length() <= MAX_RETURN_KEYWORD_LENGTH && keyword.chars().noneMatch(Character::isISOControl);
	}

	@PostMapping("/items/{goodId}/quantity")
	public String changeQuantity(@AuthenticationPrincipal MemberUserDetails member, @PathVariable long goodId,
			@Valid QuantityForm form, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", GuestCart.QUANTITY_RANGE_MESSAGE);
			return "redirect:/cart";
		}
		try {
			cartService.changeQuantity(member, goodId, form.quantity());
		} catch (InvalidCartRequestException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/cart";
	}

	@PostMapping("/items/{goodId}/delete")
	public String remove(@AuthenticationPrincipal MemberUserDetails member, @PathVariable long goodId) {
		cartService.remove(member, goodId);
		return "redirect:/cart";
	}
}
