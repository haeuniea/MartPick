package com.martpick.member;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회원가입 화면. 성공하면 로그인 화면으로 redirect한다 (PRG).
 * 검증에 실패한 입력은 화면에 다시 표시하되, 비밀번호와 확인 값은 템플릿에서 출력하지 않는다.
 */
@Controller
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@GetMapping("/signup")
	public String signupForm(Model model) {
		model.addAttribute("signupForm", new SignupForm("", "", ""));
		return "signup";
	}

	@PostMapping("/signup")
	public String signup(@Valid SignupForm signupForm, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
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
