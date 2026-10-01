package com.martpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	@Mock
	private MemberRepository memberRepository;

	@Spy
	private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@InjectMocks
	private MemberService memberService;

	@Test
	void 비밀번호를_BCrypt로_해시해_저장한다() {
		given(memberRepository.existsByUsername("tester01")).willReturn(false);

		memberService.signup("tester01", "password1");

		ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
		then(memberRepository).should().saveAndFlush(saved.capture());
		assertThat(saved.getValue().getUsername()).isEqualTo("tester01");
		assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password1").startsWith("$2");
		assertThat(passwordEncoder.matches("password1", saved.getValue().getPasswordHash())).isTrue();
	}

	@Test
	void 이미_있는_아이디이면_저장하지_않고_중복_예외를_던진다() {
		given(memberRepository.existsByUsername("tester01")).willReturn(true);

		assertThatThrownBy(() -> memberService.signup("tester01", "password1"))
				.isInstanceOf(DuplicateUsernameException.class);

		then(memberRepository).should(never()).saveAndFlush(any());
	}

	@Test
	void 사전_확인_이후_동시_가입으로_아이디_유니크_제약에_걸리면_중복_예외로_바꾼다() {
		given(memberRepository.existsByUsername("tester01")).willReturn(false);
		given(memberRepository.saveAndFlush(any(Member.class)))
				.willThrow(uniqueViolation("uk_member_username"));

		assertThatThrownBy(() -> memberService.signup("tester01", "password1"))
				.isInstanceOf(DuplicateUsernameException.class);
	}

	@Test
	void 다른_제약_위반은_중복_가입으로_바꾸지_않고_그대로_던진다() {
		given(memberRepository.existsByUsername("tester01")).willReturn(false);
		DataIntegrityViolationException other = uniqueViolation("some_other_constraint");
		given(memberRepository.saveAndFlush(any(Member.class))).willThrow(other);

		assertThatThrownBy(() -> memberService.signup("tester01", "password1")).isSameAs(other);
	}

	@Test
	void 제약_정보가_없는_데이터_오류는_중복_가입으로_바꾸지_않고_그대로_던진다() {
		given(memberRepository.existsByUsername("tester01")).willReturn(false);
		DataIntegrityViolationException unknown = new DataIntegrityViolationException("원인 불명");
		given(memberRepository.saveAndFlush(any(Member.class))).willThrow(unknown);

		assertThatThrownBy(() -> memberService.signup("tester01", "password1")).isSameAs(unknown);
	}

	@Test
	void 아이디_유니크_제약_위반만_중복으로_판별한다() {
		assertThat(MemberService.isUsernameUniqueViolation(uniqueViolation("uk_member_username"))).isTrue();
		assertThat(MemberService.isUsernameUniqueViolation(uniqueViolation("UK_MEMBER_USERNAME"))).isTrue();
		assertThat(MemberService.isUsernameUniqueViolation(uniqueViolation("uk_cart_item_member_product"))).isFalse();
		assertThat(MemberService.isUsernameUniqueViolation(new DataIntegrityViolationException("x"))).isFalse();
		assertThat(MemberService.isUsernameUniqueViolation(null)).isFalse();
	}

	private static DataIntegrityViolationException uniqueViolation(String constraintName) {
		return new DataIntegrityViolationException("제약 위반",
				new ConstraintViolationException("제약 위반", new SQLException("duplicate key"), "sql", ConstraintKind.UNIQUE, constraintName));
	}
}
