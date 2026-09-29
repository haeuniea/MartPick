package com.martpick.catalog;

import java.time.LocalDate;
import java.util.Collection;

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
}
