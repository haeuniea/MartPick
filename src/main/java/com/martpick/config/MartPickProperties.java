package com.martpick.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param inspectDay 가격 비교와 수집에 공통으로 쓰는 기준 조사일 (PROJECT_SPEC R1)
 */
@ConfigurationProperties("martpick")
public record MartPickProperties(LocalDate inspectDay, Collect collect) {

	/**
	 * @param run 수집 실행 여부. true일 때만 애플리케이션 기동 시 1회 수집한다.
	 * @param goodIds 수집 대상 상품 ID (PROJECT_SPEC 4-2)
	 * @param excludedStoreIds 개발자가 직접 확인한 비교 제외 판매점 ID (PROJECT_SPEC R8)
	 * @param allowShrink 가격 급감 안전장치에 걸려도 저장을 허용할지 여부
	 */
	public record Collect(boolean run, List<Long> goodIds, List<Long> excludedStoreIds, boolean allowShrink) {
	}
}
