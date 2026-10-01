package com.martpick.cart;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 장바구니 항목. 조회·변경·삭제는 항상 회원 ID로 범위를 제한한다 (PROJECT_SPEC R11).
 * 항목 ID나 상품 ID만으로 다른 회원의 항목에 닿는 메서드는 만들지 않는다.
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

	/**
	 * 담은 순서(항목 ID 오름차순)로 회원의 항목을 상품과 함께 한 번에 읽는다. 상품 조회가 항목 수만큼 늘어나지 않는다.
	 */
	@EntityGraph(attributePaths = "product")
	List<CartItem> findByMemberIdOrderByIdAsc(Long memberId);

	Optional<CartItem> findByMemberIdAndProductGoodId(Long memberId, Long goodId);

	long deleteByMemberIdAndProductGoodId(Long memberId, Long goodId);
}
