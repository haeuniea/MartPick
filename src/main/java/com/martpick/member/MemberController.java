package com.martpick.member;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.martpick.config.ContinueUrl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회원가입·로그인 화면. 가입에 성공하면 로그인 화면으로 redirect한다 (PRG). 가입 경로에서도 허용된 {@code continue}를 유지한다.
 * 검증에 실패한 입력은 화면에 다시 표시하되, 비밀번호와 확인 값은 템플릿에서 출력하지 않는다.
 * 이미 로그인한 사용자는 로그인·가입 화면 대신 상품 탐색 화면으로 보낸다.
 * 로그인 처리(POST /login)와 로그아웃은 Spring Security 필터가 맡는다.
 */
@Controller
@RequiredArgsConstructor
public class MemberController {

	private static final String REDIRECT_PRODUCTS = "redirect:/products";

	private final MemberService memberService;

	@GetMapping("/login")
	public String loginForm(@AuthenticationPrincipal MemberUserDetails member,
			@RequestParam(name = "continue", required = false) String continueUrl,
			@RequestParam(required = false) String error, Model model) {
		if (member != null) {
			return REDIRECT_PRODUCTS;
		}
		model.addAttribute("continueUrl", ContinueUrl.resolve(continueUrl));
		if (error != null) {
			model.addAttribute("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
		}
		return "login";
	}

	@GetMapping("/signup")
	public String signupForm(@AuthenticationPrincipal MemberUserDetails member,
			@RequestParam(name = "continue", required = false) String continueUrl, Model model) {
		if (member != null) {
			return REDIRECT_PRODUCTS;
		}
		model.addAttribute("signupForm", new SignupForm("", "", ""));
		model.addAttribute("continueUrl", ContinueUrl.resolve(continueUrl));
		return "signup";
	}

	/**
	 * 가입에 성공해도 자동으로 로그인하지 않고 로그인 화면으로 보낸다. 장바구니 등에서 가입으로 넘어온 경우 로그인 후 돌아갈 화면
	 * ({@code continue})을 로그인 화면까지 넘긴다. {@code continue}는 화면에 쓰거나 이동에 쓰기 전에 매번 {@link ContinueUrl}로 다시 검증한다.
	 */
	@PostMapping("/signup")
	public String signup(@AuthenticationPrincipal MemberUserDetails member, @Valid SignupForm signupForm,
			BindingResult bindingResult, @RequestParam(name = "continue", required = false) String continueUrl,
			Model model, RedirectAttributes redirectAttributes) {
		if (member != null) {
			return REDIRECT_PRODUCTS;
		}
		String target = ContinueUrl.resolve(continueUrl);
		if (bindingResult.hasErrors()) {
			model.addAttribute("continueUrl", target);
			return "signup";
		}
		try {
			memberService.signup(signupForm.username(), signupForm.password());
		} catch (DuplicateUsernameException e) {
			bindingResult.rejectValue("username", "duplicate", "이미 사용 중인 아이디입니다.");
			model.addAttribute("continueUrl", target);
			return "signup";
		}
		redirectAttributes.addFlashAttribute("message", "회원가입이 완료되었습니다. 로그인해 주세요.");
		if (target != null) {
			redirectAttributes.addAttribute("continue", target);
		}
		return "redirect:/login";
	}
}
