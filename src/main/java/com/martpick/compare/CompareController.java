package com.martpick.compare;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.martpick.cart.CartService;
import com.martpick.compare.InvalidComparisonRequestException.Reason;
import com.martpick.member.MemberUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 장바구니로 선택 지역의 판매점 가격을 비교한다. 비회원은 세션 장바구니를, 로그인한 회원은 DB 장바구니를 입력으로 쓴다.
 * 조회만 하므로 GET으로 처리한다. 결과 화면에서 다른 지역으로 다시 비교하는 요청도 같은 GET이며, 지역은 요청에만 쓰고 저장하지 않는다.
 * 비교 요청 오류는 장바구니 화면으로 돌려보내 사유를 안내한다 (PROJECT_SPEC 5장).
 */
@Controller
@RequiredArgsConstructor
public class CompareController {

	/** 결과 화면에서 완전 비교와 부분 비교를 각각 처음 몇 곳까지 펼쳐 보여 줄지. 나머지는 접어서 보여 준다. */
	static final int PREVIEW_COUNT = 10;

	private final ComparisonService comparisonService;
	private final CartService cartService;

	@GetMapping("/compare")
	public String compare(@AuthenticationPrincipal MemberUserDetails member,
			@RequestParam(required = false) String areaCode, Model model, RedirectAttributes redirectAttributes) {
		ComparisonResult result;
		try {
			result = comparisonService.compare(areaCode, cartService.lines(member));
		} catch (InvalidComparisonRequestException e) {
			redirectAttributes.addFlashAttribute("error", messageFor(e.getReason()));
			return "redirect:/cart";
		}
		model.addAttribute("result", result);
		model.addAttribute("regions", comparisonService.comparableRegions());
		model.addAttribute("previewCount", PREVIEW_COUNT);
		model.addAttribute("inspectDay", result.inspectDay());
		return "compare";
	}

	private static String messageFor(Reason reason) {
		return switch (reason) {
			case EMPTY_CART -> "장바구니가 비어 있습니다. 비교할 상품을 담아 주세요.";
			case REGION_REQUIRED -> "비교할 지역을 선택해 주세요.";
			case UNKNOWN_REGION -> "비교할 수 없는 지역입니다. 목록에서 지역을 다시 선택해 주세요.";
			case UNKNOWN_PRODUCT -> "장바구니에 비교할 수 없는 상품이 있습니다. 장바구니를 확인해 주세요.";
			case INVALID_CART_LINE, QUANTITY_OUT_OF_RANGE, DUPLICATE_PRODUCT ->
					"장바구니 정보가 올바르지 않습니다. 장바구니를 확인해 주세요.";
		};
	}
}
