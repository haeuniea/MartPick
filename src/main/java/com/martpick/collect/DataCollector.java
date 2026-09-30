package com.martpick.collect;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.martpick.catalog.Price;
import com.martpick.catalog.PriceRepository;
import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.catalog.Region;
import com.martpick.catalog.RegionRepository;
import com.martpick.catalog.Store;
import com.martpick.catalog.StoreRepository;
import com.martpick.config.MartPickProperties;
import com.martpick.publicdata.PriceRow;
import com.martpick.publicdata.ProductRow;
import com.martpick.publicdata.PublicDataApiException;
import com.martpick.publicdata.PublicDataClient;
import com.martpick.publicdata.RegionRow;
import com.martpick.publicdata.StoreRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터를 모두 조회·검증한 뒤 한 트랜잭션으로 저장한다.
 * API 호출이나 검증이 하나라도 실패하면 DB를 변경하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCollector {

	/** 공통 코드 API에서 시·도 코드의 상위 코드 (API_RESEARCH 3-2, 4장) */
	private static final String SIDO_HIGH_CODE = "020000000";
	private static final String HEADQUARTERS_PATTERN = "(본사)";

	/**
	 * 가격 급감 안전장치 비율. 데이터 완전성을 증명하는 기준이 아니라,
	 * 부분 응답으로 기존 가격을 잘못 지우는 일을 막기 위한 임시 안전장치다.
	 */
	private static final double SHRINK_GUARD_RATIO = 0.8;

	private final PublicDataClient client;
	private final MartPickProperties properties;
	private final RegionRepository regionRepository;
	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;
	private final PriceRepository priceRepository;
	private final TransactionTemplate transactionTemplate;

	public CollectionReport collect() {
		LocalDate inspectDay = properties.inspectDay();
		MartPickProperties.Collect config = properties.collect();
		List<Long> goodIds = config.goodIds();
		Set<Long> excludedIds = Set.copyOf(config.excludedStoreIds());

		Map<String, Integer> retries = new LinkedHashMap<>();
		List<RegionRow> regionRows = withRetry("지역 코드", client::fetchRegions, retries);
		List<StoreRow> storeRows = withRetry("판매점", client::fetchStores, retries);
		List<ProductRow> productRows = withRetry("상품", client::fetchProducts, retries);
		Map<Long, List<PriceRow>> priceRows = new LinkedHashMap<>();
		for (Long goodId : goodIds) {
			priceRows.put(goodId, withRetry("가격 goodId=" + goodId, () -> client.fetchPrices(inspectDay, goodId), retries));
		}

		List<RegionRow> sidoRows = regionRows.stream().filter(row -> SIDO_HIGH_CODE.equals(row.highCode())).toList();
		List<ProductRow> targetProducts = productRows.stream().filter(row -> goodIds.contains(row.goodId())).toList();
		validate(sidoRows, storeRows, targetProducts, goodIds, priceRows);

		List<String> warnings = new ArrayList<>();
		warnRoundCounts(storeRows, priceRows, warnings);
		warnHeadquartersMismatch(storeRows, excludedIds, warnings);

		transactionTemplate.executeWithoutResult(status -> {
			guardShrink(inspectDay, priceRows, config.allowShrink(), warnings);
			warnMissingStores(storeRows, warnings);
			save(inspectDay, sidoRows, storeRows, targetProducts, priceRows, excludedIds);
		});

		Map<Long, Integer> priceCounts = new LinkedHashMap<>();
		priceRows.forEach((goodId, rows) -> priceCounts.put(goodId, rows.size()));
		int excludedCount = (int) storeRows.stream().filter(row -> exclusionReason(row, excludedIds) != null).count();
		CollectionReport report = new CollectionReport(inspectDay, sidoRows.size(), storeRows.size(), excludedCount,
				targetProducts.size(), priceCounts, priceCounts.values().stream().mapToInt(Integer::intValue).sum(),
				Map.copyOf(retries), List.copyOf(warnings));
		log.info("공공데이터 수집 완료: {}", report);
		return report;
	}

	/**
	 * 일시적 오류(네트워크 오류·타임아웃, HTTP 5xx)만 설정된 대기 시간 간격으로 재시도한다 (PROJECT_SPEC 4-1-1).
	 * 재시도 대상이 아니거나 호출당 재시도 횟수를 다 쓰면 마지막 예외를 그대로 던지고,
	 * 수집 1회의 재시도 총합이 상한에 도달하면 더 재시도하지 않고 중단한다.
	 *
	 * @param retries 호출 구분별 재시도 횟수. 수집 1회 동안 공유한다.
	 */
	private <T> T withRetry(String label, Supplier<T> call, Map<String, Integer> retries) {
		List<Duration> delays = properties.collect().retryDelays();
		int maxTotalRetries = properties.collect().maxTotalRetries();
		for (int attempt = 0; ; attempt++) {
			try {
				return call.get();
			} catch (PublicDataApiException e) {
				if (!e.isRetryable() || attempt >= delays.size()) {
					throw e;
				}
				int used = retries.values().stream().mapToInt(Integer::intValue).sum();
				if (used >= maxTotalRetries) {
					throw new DataCollectionException("수집 1회의 재시도 총합 상한 " + maxTotalRetries + "회에 도달해 중단합니다: "
							+ label + " (" + e.getMessage() + ")");
				}
				retries.merge(label, 1, Integer::sum);
				log.warn("일시적 오류로 재시도 {}/{}: {} ({})", attempt + 1, delays.size(), label, e.getMessage());
				sleep(delays.get(attempt));
			}
		}
	}

	private static void sleep(Duration delay) {
		try {
			Thread.sleep(delay.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new DataCollectionException("재시도 대기 중 중단되었습니다.");
		}
	}

	private void validate(List<RegionRow> sidoRows, List<StoreRow> storeRows, List<ProductRow> targetProducts,
			List<Long> goodIds, Map<Long, List<PriceRow>> priceRows) {
		if (sidoRows.isEmpty()) {
			throw new DataCollectionException("V1: 지역 코드 응답에 시·도 코드(highCode=" + SIDO_HIGH_CODE + ")가 없습니다.");
		}
		Set<String> sidoCodes = sidoRows.stream().map(RegionRow::code).collect(Collectors.toSet());
		Set<String> unknownAreas = storeRows.stream().map(StoreRow::areaCode)
				.filter(code -> !sidoCodes.contains(code)).collect(Collectors.toCollection(TreeSet::new));
		if (!unknownAreas.isEmpty()) {
			throw new DataCollectionException("V1: 시·도 목록에 없는 판매점 지역 코드: " + unknownAreas);
		}

		Set<Long> foundProducts = targetProducts.stream().map(ProductRow::goodId).collect(Collectors.toSet());
		List<Long> missingProducts = goodIds.stream().filter(id -> !foundProducts.contains(id)).toList();
		if (!missingProducts.isEmpty()) {
			throw new DataCollectionException("V2: 상품 응답에 없는 수집 대상 상품: " + missingProducts);
		}

		Set<Long> storeIds = new HashSet<>();
		List<Long> duplicateStores = storeRows.stream().map(StoreRow::entpId).filter(id -> !storeIds.add(id)).toList();
		if (!duplicateStores.isEmpty()) {
			throw new DataCollectionException("V3: 판매점 ID 중복: " + duplicateStores);
		}

		// 초기 수집 정책: 사전 조사(API_RESEARCH 6-3)에서 대상 상품 7개의 기준 조사일 가격이 확인되었으므로
		// 0건을 실패로 본다. API의 정상 빈 응답(resultCode=00, 빈 result) 자체를 오류로 보는 것은 아니다.
		List<Long> emptyPrices = priceRows.entrySet().stream()
				.filter(entry -> entry.getValue().isEmpty()).map(Map.Entry::getKey).toList();
		if (!emptyPrices.isEmpty()) {
			throw new DataCollectionException("V4: 기준 조사일 가격이 0건인 수집 대상 상품: " + emptyPrices
					+ " (사전 조사에서 가격이 확인된 상품이므로 초기 수집 정책상 실패로 처리)");
		}

		Set<Long> unknownStores = new TreeSet<>();
		Set<String> priceKeys = new HashSet<>();
		List<String> duplicatePrices = new ArrayList<>();
		priceRows.values().forEach(rows -> rows.forEach(row -> {
			if (!storeIds.contains(row.entpId())) {
				unknownStores.add(row.entpId());
			}
			String key = row.entpId() + "-" + row.goodId() + "-" + row.inspectDay();
			if (!priceKeys.add(key)) {
				duplicatePrices.add(key);
			}
		}));
		if (!unknownStores.isEmpty()) {
			throw new DataCollectionException("V5: 판매점 응답에 없는 가격의 판매점 ID: " + unknownStores);
		}
		if (!duplicatePrices.isEmpty()) {
			throw new DataCollectionException("V6: 같은 판매점·상품·조사일의 가격 중복: " + duplicatePrices);
		}
	}

	private void warnRoundCounts(List<StoreRow> storeRows, Map<Long, List<PriceRow>> priceRows, List<String> warnings) {
		if (isRound(storeRows.size())) {
			warnings.add("참고 신호: 판매점 응답 건수가 " + storeRows.size() + "건으로 딱 떨어집니다. 누락 여부를 확인하세요.");
		}
		priceRows.forEach((goodId, rows) -> {
			if (isRound(rows.size())) {
				warnings.add("참고 신호: 상품 " + goodId + " 가격 응답 건수가 " + rows.size() + "건으로 딱 떨어집니다. 누락 여부를 확인하세요.");
			}
		});
	}

	private static boolean isRound(int count) {
		return count > 0 && count % 100 == 0;
	}

	private void warnHeadquartersMismatch(List<StoreRow> storeRows, Set<Long> excludedIds, List<String> warnings) {
		List<Long> patternOnly = storeRows.stream()
				.filter(row -> row.name().contains(HEADQUARTERS_PATTERN) && !excludedIds.contains(row.entpId()))
				.map(StoreRow::entpId).toList();
		if (!patternOnly.isEmpty()) {
			warnings.add("판매점명에 (본사)가 있으나 제외 ID 목록에 없는 판매점: " + patternOnly);
		}
		Set<Long> responseIds = storeRows.stream().map(StoreRow::entpId).collect(Collectors.toSet());
		List<Long> absentExcluded = excludedIds.stream().filter(id -> !responseIds.contains(id)).sorted().toList();
		if (!absentExcluded.isEmpty()) {
			warnings.add("제외 ID 목록에 있으나 판매점 응답에 없는 ID: " + absentExcluded);
		}
	}

	private void guardShrink(LocalDate inspectDay, Map<Long, List<PriceRow>> priceRows, boolean allowShrink,
			List<String> warnings) {
		priceRows.forEach((goodId, rows) -> {
			long existing = priceRepository.countByInspectDayAndProductGoodId(inspectDay, goodId);
			if (existing == 0 || rows.size() >= existing * SHRINK_GUARD_RATIO) {
				return;
			}
			String message = "상품 " + goodId + " 가격이 기존 " + existing + "건에서 " + rows.size() + "건으로 "
					+ (int) (SHRINK_GUARD_RATIO * 100) + "% 미만으로 줄었습니다 (임시 안전장치, 완전성 증명 기준 아님)";
			if (!allowShrink) {
				throw new DataCollectionException(message + ". 의도한 감소라면 martpick.collect.allow-shrink=true로 실행하세요.");
			}
			warnings.add(message + ". allow-shrink 옵션으로 저장을 허용했습니다.");
		});
	}

	private void warnMissingStores(List<StoreRow> storeRows, List<String> warnings) {
		Set<Long> responseIds = storeRows.stream().map(StoreRow::entpId).collect(Collectors.toSet());
		List<Long> missing = storeRepository.findAll().stream().map(Store::getEntpId)
				.filter(id -> !responseIds.contains(id)).sorted().toList();
		if (!missing.isEmpty()) {
			warnings.add("응답에 없는 기존 판매점 " + missing.size() + "곳 (삭제하지 않음): " + missing);
		}
	}

	private void save(LocalDate inspectDay, List<RegionRow> sidoRows, List<StoreRow> storeRows,
			List<ProductRow> targetProducts, Map<Long, List<PriceRow>> priceRows, Set<Long> excludedIds) {
		regionRepository.saveAll(sidoRows.stream().map(row -> new Region(row.code(), row.name())).toList());
		storeRepository.saveAll(storeRows.stream().map(row -> toStore(row, excludedIds)).toList());
		productRepository.saveAll(targetProducts.stream()
				.map(row -> new Product(row.goodId(), row.name(), row.totalCnt(), row.totalDivCode())).toList());
		priceRepository.flush();

		priceRepository.deleteByInspectDayAndGoodIds(inspectDay, priceRows.keySet());
		List<Price> prices = new ArrayList<>();
		priceRows.values().forEach(rows -> rows.forEach(row -> prices.add(new Price(
				storeRepository.getReferenceById(row.entpId()),
				productRepository.getReferenceById(row.goodId()),
				row.inspectDay(), row.price()))));
		priceRepository.saveAll(prices);
		priceRepository.flush();
	}

	private static Store toStore(StoreRow row, Set<Long> excludedIds) {
		String reason = exclusionReason(row, excludedIds);
		return new Store(row.entpId(), row.name(), row.typeCode(), row.areaCode(), row.areaDetailCode(),
				row.roadAddrBasic(), row.roadAddrDetail(), row.plmkAddrBasic(), row.plmkAddrDetail(),
				reason != null, reason);
	}

	private static String exclusionReason(StoreRow row, Set<Long> excludedIds) {
		boolean pattern = row.name().contains(HEADQUARTERS_PATTERN);
		boolean listed = excludedIds.contains(row.entpId());
		if (pattern && listed) {
			return "판매점명 (본사) 패턴, 제외 ID 목록";
		}
		if (pattern) {
			return "판매점명 (본사) 패턴";
		}
		return listed ? "제외 ID 목록" : null;
	}
}
