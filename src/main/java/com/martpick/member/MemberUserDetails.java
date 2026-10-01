package com.martpick.member;

import java.util.List;

import org.springframework.security.core.userdetails.User;

/**
 * 인증된 회원. 세션의 인증 정보에서 회원 ID를 바로 얻기 위해 {@link User}에 회원 ID를 더한다.
 * 장바구니 등 회원 데이터는 요청 파라미터가 아니라 이 ID로만 조회한다 (PROJECT_SPEC R11).
 */
public class MemberUserDetails extends User {

	private static final long serialVersionUID = 1L;

	private final long memberId;

	public MemberUserDetails(long memberId, String username, String passwordHash) {
		super(username, passwordHash, List.of());
		this.memberId = memberId;
	}

	public long getMemberId() {
		return memberId;
	}
}
