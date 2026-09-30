package com.martpick.catalog;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceRepository extends JpaRepository<Price, Long> {

	long countByInspectDayAndProductGoodId(LocalDate inspectDay, Long goodId);

	long countByInspectDay(LocalDate inspectDay);

	/**
	 * 기준 조사일 가격이 1건 이상인 상품 수.
	 */
	@Query("select count(distinct p.product.goodId) from Price p where p.inspectDay = :inspectDay")
	long countPricedProductsByInspectDay(@Param("inspectDay") LocalDate inspectDay);

	/**
	 * 기준 조사일의 가격을 모두 지운다. 전체 수집의 가격 교체에만 사용한다 (PROJECT_SPEC 4-1-1).
	 */
	@Modifying
	@Query("delete from Price p where p.inspectDay = :inspectDay")
	int deleteByInspectDay(@Param("inspectDay") LocalDate inspectDay);

	/**
	 * 기준 조사일·선택 지역·비교 대상(본사 제외) 판매점의 장바구니 상품 가격을 한 번에 조회한다.
	 */
	@Query("""
			select new com.martpick.catalog.StorePriceRow(s.entpId, s.name, s.roadAddrBasic, s.roadAddrDetail,
			    s.plmkAddrBasic, s.plmkAddrDetail, p.product.goodId, p.price)
			from Price p join p.store s
			where p.inspectDay = :inspectDay and s.areaCode = :areaCode and s.excluded = false
			  and p.product.goodId in :goodIds
			""")
	List<StorePriceRow> findStorePrices(@Param("inspectDay") LocalDate inspectDay,
			@Param("areaCode") String areaCode, @Param("goodIds") Collection<Long> goodIds);
}
