package com.martpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 서비스·PostgreSQL 제약까지 거치는 회원가입 흐름. 테스트 데이터는 트랜잭션 롤백으로 남기지 않는다.
 * DB 오류가 나면 PostgreSQL 트랜잭션이 중단되므로, 오류를 일으키는 테스트는 그 한 가지만 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberSignupIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberService memberService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void 가입하면_BCrypt_해시가_저장되고_평문은_저장되지_않는다() {
		memberService.signup("itest01", "plain-password-1");

		Member saved = find("itest01");
		assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo("plain-password-1");
		assertThat(passwordEncoder.matches("plain-password-1", saved.getPasswordHash())).isTrue();
		assertThat(saved.getCreatedAt()).isNotNull();
	}

	@Test
	void 같은_아이디로_다시_가입하면_중복_예외이고_기존_회원은_바뀌지_않는다() {
		memberService.signup("itest01", "first-password-1");
		String firstHash = find("itest01").getPasswordHash();

		assertThatThrownBy(() -> memberService.signup("itest01", "second-password-2"))
				.isInstanceOf(DuplicateUsernameException.class);

		assertThat(find("itest01").getPasswordHash()).isEqualTo(firstHash);
	}

	@Test
	void 아이디는_대소문자를_구분해_서로_다른_아이디로_가입할_수_있다() {
		memberService.signup("ItestAbc", "password-1234");
		memberService.signup("itestabc", "password-1234");

		assertThat(memberRepository.existsByUsername("ItestAbc")).isTrue();
		assertThat(memberRepository.existsByUsername("itestabc")).isTrue();
		assertThat(memberRepository.existsByUsername("ITESTABC")).isFalse();
	}

	@Test
	void 컨트롤러부터_DB까지_가입이_성공하면_로그인_화면으로_이동한다() throws Exception {
		mockMvc.perform(post("/signup").with(csrf())
						.param("username", "itest02").param("password", "password-1234").param("passwordConfirm", "password-1234"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));

		assertThat(memberRepository.existsByUsername("itest02")).isTrue();
	}

	@Test
	void 비밀번호_확인이_다르면_회원이_생성되지_않는다() throws Exception {
		mockMvc.perform(post("/signup").with(csrf())
						.param("username", "itest03").param("password", "password-1234").param("passwordConfirm", "different-1234"))
				.andExpect(status().isOk());

		assertThat(memberRepository.existsByUsername("itest03")).isFalse();
	}

	@Test
	void 실제_PostgreSQL의_아이디_유니크_제약_위반을_중복으로_판별한다() {
		memberRepository.saveAndFlush(new Member("itest04", "$2a$10$hash"));

		DataIntegrityViolationException duplicate = catchDataIntegrityViolation(
				() -> memberRepository.saveAndFlush(new Member("itest04", "$2a$10$other")));

		assertThat(MemberService.isUsernameUniqueViolation(duplicate)).isTrue();
	}

	@Test
	void 실제_PostgreSQL의_아이디_길이_초과_오류는_중복으로_판별하지_않는다() {
		DataIntegrityViolationException tooLong = catchDataIntegrityViolation(
				() -> memberRepository.saveAndFlush(new Member("a".repeat(21), "$2a$10$hash")));

		assertThat(MemberService.isUsernameUniqueViolation(tooLong)).isFalse();
	}

	private Member find(String username) {
		return memberRepository.findAll().stream()
				.filter(member -> member.getUsername().equals(username))
				.findFirst().orElseThrow();
	}

	private static DataIntegrityViolationException catchDataIntegrityViolation(Runnable action) {
		try {
			action.run();
		} catch (DataIntegrityViolationException e) {
			return e;
		}
		throw new AssertionError("DataIntegrityViolationException이 발생하지 않았습니다.");
	}
}
