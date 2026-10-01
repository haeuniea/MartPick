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
 * 회원가입·로그인 화면. 가입에 성공하면 로그인 화면으로 redirect한다 (PRG).
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
	public String signupForm(@AuthenticationPrincipal MemberUserDetails member, Model model) {
		if (member != null) {
			return REDIRECT_PRODUCTS;
		}
		model.addAttribute("signupForm", new SignupForm("", "", ""));
		return "signup";
	}

	@PostMapping("/signup")
	public String signup(@AuthenticationPrincipal MemberUserDetails member, @Valid SignupForm signupForm,
			BindingResult bindingResult, RedirectAttributes redirectAttributes) {
		if (member != null) {
			return REDIRECT_PRODUCTS;
		}
		if (bindingResult.hasErrors()) {
			return "signup";
		}
		try {
			memberService.signup(signupForm.username(), signupForm.password());
		} catch (DuplicateUsernameException e) {
			bindingResult.rejectValue("username", "duplicate", "이미 사용 중인 아이디입니다.");
			return "signup";
		}
		redirectAttributes.addFlashAttribute("message", "회원가입이 완료되었습니다. 로그인해 주세요.");
		return "redirect:/login";
	}
}
