package com.martpick.catalog;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionRepository extends JpaRepository<Region, String> {

	/**
	 * 기준 조사일에 가격이 있는 비교 대상(본사 제외) 판매점이 속한 시·도 (PROJECT_SPEC R12).
	 * price는 수집 대상 상품(product)만 참조하므로 상품 범위 조건은 따로 두지 않는다.
	 */
	@Query("""
			select r from Region r
			where exists (
			    select p.id from Price p join p.store s
			    where s.areaCode = r.code and p.inspectDay = :inspectDay and s.excluded = false)
			order by r.code
			""")
	List<Region> findComparableRegions(@Param("inspectDay") LocalDate inspectDay);
}
