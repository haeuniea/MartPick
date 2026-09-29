package com.martpick.compare;

import java.util.List;

/**
 * 가격 계산 결과. 조사일·지역 정보는 {@link ComparisonService}가 {@link ComparisonResult}로 합친다.
 */
record ComparedStores(List<CompleteStore> completeStores, List<PartialStore> partialStores) {
}
