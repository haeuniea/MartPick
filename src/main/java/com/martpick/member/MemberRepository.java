package com.martpick.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {

	boolean existsByUsername(String username);

	Optional<Member> findByUsername(String username);

	/**
	 * 회원 행에 쓰기 잠금을 걸고 읽는다. 같은 회원의 장바구니를 바꾸는 작업을 직렬화하는 데 쓴다.
	 * 반드시 트랜잭션 안에서 호출해야 하며, 잠금은 그 트랜잭션이 끝날 때 풀린다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Member> findWithLockById(Long id);
}
