package com.martpick.collect;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRunRepository extends JpaRepository<CollectionRun, Long> {

	/**
	 * 같은 조사일의 가장 최근 성공 이력. 완료 시각이 같으면 나중에 저장한 이력을 최신으로 본다.
	 */
	Optional<CollectionRun> findFirstByInspectDayOrderByCompletedAtDescIdDesc(LocalDate inspectDay);
}
