package com.martpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberUserDetailsServiceTest {

	@Mock
	private MemberRepository memberRepository;

	@InjectMocks
	private MemberUserDetailsService service;

	@Test
	void 회원_ID와_아이디와_해시를_가진_사용자_정보를_반환한다() {
		Member member = new Member("tester01", "$2a$10$hash");
		ReflectionTestUtils.setField(member, "id", 7L);
		given(memberRepository.findByUsername("tester01")).willReturn(Optional.of(member));

		UserDetails found = service.loadUserByUsername("tester01");

		assertThat(found).isInstanceOf(MemberUserDetails.class);
		assertThat(((MemberUserDetails) found).getMemberId()).isEqualTo(7L);
		assertThat(found.getUsername()).isEqualTo("tester01");
		assertThat(found.getPassword()).isEqualTo("$2a$10$hash");
		assertThat(found.getAuthorities()).isEmpty();
		assertThat(found.isEnabled() && found.isAccountNonLocked() && found.isAccountNonExpired()
				&& found.isCredentialsNonExpired()).isTrue();
	}

	@Test
	void 없는_아이디는_UsernameNotFoundException이다() {
		given(memberRepository.findByUsername("nobody")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.loadUserByUsername("nobody")).isInstanceOf(UsernameNotFoundException.class);
	}
}
