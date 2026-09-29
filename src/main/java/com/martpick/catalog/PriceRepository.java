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

	@Modifying
	@Query("delete from Price p where p.inspectDay = :inspectDay and p.product.goodId in :goodIds")
	int deleteByInspectDayAndGoodIds(@Param("inspectDay") LocalDate inspectDay,
			@Param("goodIds") Collection<Long> goodIds);

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
