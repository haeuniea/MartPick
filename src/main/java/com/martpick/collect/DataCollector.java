package com.martpick.collect;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * 공공데이터 API 상품 응답의 전체 상품을 대상으로 모두 조회·검증한 뒤 한 트랜잭션으로 저장한다 (PROJECT_SPEC 4-1-1).
 * API 호출이나 검증이 하나라도 실패하면 DB를 변경하지 않는다.
 * 기준 상품(baselineGoodIds)은 수집 범위가 아니라 필수 검증(V2, V4)과 회귀 검증용 급감 안전장치에만 쓴다.
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

	/** 수집 보고의 상품별 가격 건수 분포 구간 크기 */
	private static final int DISTRIBUTION_BUCKET = 100;

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
		List<Long> baselineIds = config.baselineGoodIds();
		Set<Long> excludedIds = Set.copyOf(config.excludedStoreIds());

		Map<String, Integer> retries = new LinkedHashMap<>();
		List<RegionRow> regionRows = withRetry("지역 코드", client::fetchRegions, retries);
		List<StoreRow> storeRows = withRetry("판매점", client::fetchStores, retries);
		List<ProductRow> productRows = withRetry("상품", client::fetchProducts, retries);

		// 가격 호출(상품 수만큼)을 하기 전에 검증할 수 있는 것은 먼저 검증해 호출 한도를 아낀다.
		List<RegionRow> sidoRows = regionRows.stream().filter(row -> SIDO_HIGH_CODE.equals(row.highCode())).toList();
		validateBeforePrices(sidoRows, storeRows, productRows, baselineIds);

		Map<Long, List<PriceRow>> priceRows = new LinkedHashMap<>();
		for (ProductRow product : productRows) {
			long goodId = product.goodId();
			priceRows.put(goodId, withRetry("가격 goodId=" + goodId, () -> client.fetchPrices(inspectDay, goodId), retries));
		}
		if (priceRows.size() != productRows.size()) {
			throw new DataCollectionException("상품 응답 " + productRows.size() + "건과 가격을 조회한 상품 "
					+ priceRows.size() + "건이 다릅니다.");
		}
		validatePrices(storeRows, baselineIds, priceRows);

		List<String> warnings = new ArrayList<>();
		warnRoundCounts(storeRows, productRows, warnings);
		warnHeadquartersMismatch(storeRows, excludedIds, warnings);

		Map<Long, Long> missingProducts = new LinkedHashMap<>();
		transactionTemplate.executeWithoutResult(status -> {
			guardShrink(inspectDay, baselineIds, priceRows, config.allowShrink(), warnings);
			warnMissingStores(storeRows, warnings);
			missingProducts.putAll(findMissingProducts(inspectDay, productRows));
			if (!missingProducts.isEmpty()) {
				warnings.add("응답에 없는 기존 상품 " + missingProducts.size() + "개 (상품은 유지하고 기준 조사일 가격 "
						+ missingProducts.values().stream().mapToLong(Long::longValue).sum() + "건은 전체 교체로 삭제): "
						+ missingProducts.keySet());
			}
			save(inspectDay, sidoRows, storeRows, productRows, priceRows, excludedIds);
		});

		CollectionReport report = report(inspectDay, sidoRows, storeRows, excludedIds, productRows, priceRows,
				baselineIds, missingProducts, retries, warnings);
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

	/**
	 * V1, V2, V3: 지역·판매점·상품 응답만으로 할 수 있는 필수 검증.
	 */
	private void validateBeforePrices(List<RegionRow> sidoRows, List<StoreRow> storeRows, List<ProductRow> productRows,
			List<Long> baselineIds) {
		if (sidoRows.isEmpty()) {
			throw new DataCollectionException("V1: 지역 코드 응답에 시·도 코드(highCode=" + SIDO_HIGH_CODE + ")가 없습니다.");
		}
		Set<String> sidoCodes = sidoRows.stream().map(RegionRow::code).collect(Collectors.toSet());
		Set<String> unknownAreas = storeRows.stream().map(StoreRow::areaCode)
				.filter(code -> !sidoCodes.contains(code)).collect(Collectors.toCollection(TreeSet::new));
		if (!unknownAreas.isEmpty()) {
			throw new DataCollectionException("V1: 시·도 목록에 없는 판매점 지역 코드: " + unknownAreas);
		}

		Set<Long> productIds = new HashSet<>();
		Set<Long> duplicateProducts = productRows.stream().map(ProductRow::goodId)
				.filter(id -> !productIds.add(id)).collect(Collectors.toCollection(TreeSet::new));
		if (!duplicateProducts.isEmpty()) {
			throw new DataCollectionException("V2: 상품 응답의 상품 ID 중복: " + duplicateProducts);
		}
		List<Long> missingBaseline = baselineIds.stream().filter(id -> !productIds.contains(id)).toList();
		if (!missingBaseline.isEmpty()) {
			throw new DataCollectionException("V2: 상품 응답에 없는 기준 상품: " + missingBaseline);
		}

		Set<Long> storeIds = new HashSet<>();
		List<Long> duplicateStores = storeRows.stream().map(StoreRow::entpId).filter(id -> !storeIds.add(id)).toList();
		if (!duplicateStores.isEmpty()) {
			throw new DataCollectionException("V3: 판매점 ID 중복: " + duplicateStores);
		}
	}

	/**
	 * V4, V5, V6: 가격 응답 검증. 기준 상품 외의 상품은 가격이 0건이어도 정상이다.
	 */
	private void validatePrices(List<StoreRow> storeRows, List<Long> baselineIds, Map<Long, List<PriceRow>> priceRows) {
		// 기준 상품은 사전 조사(API_RESEARCH 6-3)에서 기준 조사일 가격이 확인되었으므로 0건이면 부분 응답을 의심한다.
		List<Long> emptyBaseline = baselineIds.stream().filter(id -> priceRows.get(id).isEmpty()).toList();
		if (!emptyBaseline.isEmpty()) {
			throw new DataCollectionException("V4: 기준 조사일 가격이 0건인 기준 상품: " + emptyBaseline);
		}

		Set<Long> storeIds = storeRows.stream().map(StoreRow::entpId).collect(Collectors.toSet());
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

	/**
	 * 판매점·상품 응답 건수에만 적용한다. 상품별 가격 건수는 상품 수만큼 많아 신호로 쓰지 않는다.
	 */
	private void warnRoundCounts(List<StoreRow> storeRows, List<ProductRow> productRows, List<String> warnings) {
		if (isRound(storeRows.size())) {
			warnings.add("참고 신호: 판매점 응답 건수가 " + storeRows.size() + "건으로 딱 떨어집니다. 누락 여부를 확인하세요.");
		}
		if (isRound(productRows.size())) {
			warnings.add("참고 신호: 상품 응답 건수가 " + productRows.size() + "건으로 딱 떨어집니다. 누락 여부를 확인하세요.");
		}
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

	/**
	 * 기준 상품별 급감 안전장치(G1). 기준 상품 외의 상품에는 적용하지 않는다.
	 */
	private void guardShrink(LocalDate inspectDay, List<Long> baselineIds, Map<Long, List<PriceRow>> priceRows,
			boolean allowShrink, List<String> warnings) {
		for (Long goodId : baselineIds) {
			int newCount = priceRows.get(goodId).size();
			long existing = priceRepository.countByInspectDayAndProductGoodId(inspectDay, goodId);
			if (existing == 0 || newCount >= existing * SHRINK_GUARD_RATIO) {
				continue;
			}
			String message = "기준 상품 " + goodId + " 가격이 기존 " + existing + "건에서 " + newCount + "건으로 "
					+ (int) (SHRINK_GUARD_RATIO * 100) + "% 미만으로 줄었습니다 (임시 안전장치, 완전성 증명 기준 아님)";
			if (!allowShrink) {
				throw new DataCollectionException(message + ". 의도한 감소라면 martpick.collect.allow-shrink=true로 실행하세요.");
			}
			warnings.add(message + ". allow-shrink 옵션으로 저장을 허용했습니다.");
		}
	}

	private void warnMissingStores(List<StoreRow> storeRows, List<String> warnings) {
		Set<Long> responseIds = storeRows.stream().map(StoreRow::entpId).collect(Collectors.toSet());
		List<Long> missing = storeRepository.findAll().stream().map(Store::getEntpId)
				.filter(id -> !responseIds.contains(id)).sorted().toList();
		if (!missing.isEmpty()) {
			warnings.add("응답에 없는 기존 판매점 " + missing.size() + "곳 (삭제하지 않음): " + missing);
		}
	}

	/**
	 * @return 상품 응답에 없는 기존 상품 ID와, 전체 교체로 삭제될 그 상품의 기준 조사일 가격 건수
	 */
	private Map<Long, Long> findMissingProducts(LocalDate inspectDay, List<ProductRow> productRows) {
		Set<Long> responseIds = productRows.stream().map(ProductRow::goodId).collect(Collectors.toSet());
		Map<Long, Long> missing = new LinkedHashMap<>();
		productRepository.findAll().stream().map(Product::getGoodId)
				.filter(id -> !responseIds.contains(id)).sorted()
				.forEach(id -> missing.put(id, priceRepository.countByInspectDayAndProductGoodId(inspectDay, id)));
		return missing;
	}

	/**
	 * 기준 조사일 가격은 이번 응답으로 전체 교체한다. 응답에서 사라진 상품의 기준 조사일 가격도 남기지 않는다.
	 */
	private void save(LocalDate inspectDay, List<RegionRow> sidoRows, List<StoreRow> storeRows,
			List<ProductRow> productRows, Map<Long, List<PriceRow>> priceRows, Set<Long> excludedIds) {
		regionRepository.saveAll(sidoRows.stream().map(row -> new Region(row.code(), row.name())).toList());
		storeRepository.saveAll(storeRows.stream().map(row -> toStore(row, excludedIds)).toList());
		productRepository.saveAll(productRows.stream()
				.map(row -> new Product(row.goodId(), row.name(), row.totalCnt(), row.totalDivCode())).toList());
		priceRepository.flush();

		priceRepository.deleteByInspectDay(inspectDay);
		List<Price> prices = new ArrayList<>();
		priceRows.values().forEach(rows -> rows.forEach(row -> prices.add(new Price(
				storeRepository.getReferenceById(row.entpId()),
				productRepository.getReferenceById(row.goodId()),
				row.inspectDay(), row.price()))));
		priceRepository.saveAll(prices);
		priceRepository.flush();
	}

	private CollectionReport report(LocalDate inspectDay, List<RegionRow> sidoRows, List<StoreRow> storeRows,
			Set<Long> excludedIds, List<ProductRow> productRows, Map<Long, List<PriceRow>> priceRows,
			List<Long> baselineIds, Map<Long, Long> missingProducts, Map<String, Integer> retries, List<String> warnings) {
		int excludedCount = (int) storeRows.stream().filter(row -> exclusionReason(row, excludedIds) != null).count();
		int pricedProductCount = (int) priceRows.values().stream().filter(rows -> !rows.isEmpty()).count();
		int totalPriceCount = priceRows.values().stream().mapToInt(List::size).sum();
		Map<Long, Integer> baselinePriceCounts = new LinkedHashMap<>();
		baselineIds.forEach(id -> baselinePriceCounts.put(id, priceRows.get(id).size()));
		return new CollectionReport(inspectDay, sidoRows.size(), storeRows.size(), excludedCount,
				productRows.size(), priceRows.size(), pricedProductCount, totalPriceCount,
				Collections.unmodifiableMap(baselinePriceCounts), priceCountDistribution(priceRows),
				Collections.unmodifiableMap(new LinkedHashMap<>(missingProducts)),
				Collections.unmodifiableMap(new LinkedHashMap<>(retries)), List.copyOf(warnings));
	}

	/**
	 * 상품별 가격 건수를 "0", "1-99", "100-199" … 구간으로 센다. 구간은 작은 건수부터 나열한다.
	 */
	private static Map<String, Integer> priceCountDistribution(Map<Long, List<PriceRow>> priceRows) {
		TreeMap<Integer, Integer> byBucket = new TreeMap<>();
		priceRows.values().forEach(rows -> byBucket.merge(
				rows.isEmpty() ? -1 : rows.size() / DISTRIBUTION_BUCKET * DISTRIBUTION_BUCKET, 1, Integer::sum));
		Map<String, Integer> distribution = new LinkedHashMap<>();
		byBucket.forEach((start, count) -> distribution.put(bucketLabel(start), count));
		return Collections.unmodifiableMap(distribution);
	}

	private static String bucketLabel(int start) {
		if (start < 0) {
			return "0";
		}
		return (start == 0 ? 1 : start) + "-" + (start + DISTRIBUTION_BUCKET - 1);
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
