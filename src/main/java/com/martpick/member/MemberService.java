package com.martpick.member;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {

	static final String USERNAME_UNIQUE_CONSTRAINT = "uk_member_username";

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * 비밀번호를 BCrypt로 해시해 회원을 저장한다.
	 * 사전 확인 이후 동시 가입으로 아이디 유니크 제약에 걸린 경우에만 중복으로 처리하고, 다른 DB 오류는 그대로 던진다.
	 * 제약 위반은 flush 시점에 드러나므로 {@code saveAndFlush}로 이 메서드 안에서 확인한다.
	 */
	@Transactional
	public void signup(String username, String rawPassword) {
		if (memberRepository.existsByUsername(username)) {
			throw new DuplicateUsernameException(username);
		}
		try {
			memberRepository.saveAndFlush(new Member(username, passwordEncoder.encode(rawPassword)));
		} catch (DataIntegrityViolationException e) {
			if (isUsernameUniqueViolation(e)) {
				throw new DuplicateUsernameException(username);
			}
			throw e;
		}
	}

	static boolean isUsernameUniqueViolation(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& USERNAME_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
				return true;
			}
			if (cause.getCause() == cause) {
				break;
			}
		}
		return false;
	}
}
