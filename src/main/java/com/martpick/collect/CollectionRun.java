package com.martpick.collect;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 성공한 전체 수집 이력 (PROJECT_SPEC 4-1-1). 가격 교체와 같은 트랜잭션에서 저장하며, 실패한 수집은 기록하지 않는다.
 * 가격 급감 안전장치 G2와 이력·DB 정합성 확인의 기준이 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private LocalDate inspectDay;

	/** 이번 수집의 API 상품 응답 건수. 응답에서 사라진 상품도 남아 있는 DB 전체 상품 수와는 다르다. */
	private int productCount;

	/** 기준 조사일 가격이 1건 이상인 상품 수 */
	private int pricedProductCount;

	/** 저장한 기준 조사일 전체 가격 건수 */
	private int priceCount;

	private Instant completedAt;

	public CollectionRun(LocalDate inspectDay, int productCount, int pricedProductCount, int priceCount,
			Instant completedAt) {
		this.inspectDay = inspectDay;
		this.productCount = productCount;
		this.pricedProductCount = pricedProductCount;
		this.priceCount = priceCount;
		this.completedAt = completedAt;
	}
}
