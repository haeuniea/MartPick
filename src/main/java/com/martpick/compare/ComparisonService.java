package com.martpick.compare;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.martpick.catalog.PriceRepository;
import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.catalog.Region;
import com.martpick.catalog.RegionRepository;
import com.martpick.catalog.StorePriceRow;
import com.martpick.compare.InvalidComparisonRequestException.Reason;
import com.martpick.config.MartPickProperties;

import lombok.RequiredArgsConstructor;

/**
 * 선택 지역의 판매점별 장바구니 가격을 비교한다. 기준 조사일은 설정값으로 고정한다 (PROJECT_SPEC R1).
 * 인증·장바구니 저장 방식과 무관하게 {@link CartLine} 목록만 입력으로 받는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComparisonService {

	private static final int MIN_QUANTITY = 1;
	private static final int MAX_QUANTITY = 99;

	private final MartPickProperties properties;
	private final RegionRepository regionRepository;
	private final ProductRepository productRepository;
	private final PriceRepository priceRepository;
	private final PriceComparator priceComparator;

	public List<RegionOption> comparableRegions() {
		return regionRepository.findComparableRegions(properties.inspectDay()).stream()
				.map(region -> new RegionOption(region.getCode(), region.getName()))
				.toList();
	}

	public ComparisonResult compare(String areaCode, List<CartLine> cart) {
		validateWithoutDatabase(areaCode, cart);
		LocalDate inspectDay = properties.inspectDay();

		Region region = regionRepository.findComparableRegions(inspectDay).stream()
				.filter(candidate -> candidate.getCode().equals(areaCode))
				.findFirst()
				.orElseThrow(() -> new InvalidComparisonRequestException(Reason.UNKNOWN_REGION,
						"비교할 수 있는 지역이 아닙니다: " + areaCode));

		List<Long> goodIds = cart.stream().map(CartLine::goodId).toList();
		List<ProductInfo> products = productRepository.findAllById(goodIds).stream()
				.map(ComparisonService::toProductInfo)
				.toList();
		if (products.size() != goodIds.size()) {
			Set<Long> found = new HashSet<>(products.stream().map(ProductInfo::goodId).toList());
			List<Long> unknown = goodIds.stream().filter(id -> !found.contains(id)).toList();
			throw new InvalidComparisonRequestException(Reason.UNKNOWN_PRODUCT, "수집되지 않은 상품: " + unknown);
		}

		List<StorePriceRow> rows = priceRepository.findStorePrices(inspectDay, areaCode, goodIds);
		ComparedStores compared = priceComparator.compare(cart, products, rows);
		return new ComparisonResult(inspectDay, region.getCode(), region.getName(),
				compared.completeStores(), compared.partialStores());
	}

	/**
	 * DB 조회 없이 할 수 있는 검증을 승인된 순서대로 수행한다.
	 */
	private static void validateWithoutDatabase(String areaCode, List<CartLine> cart) {
		if (cart == null || cart.isEmpty()) {
			throw new InvalidComparisonRequestException(Reason.EMPTY_CART, "장바구니가 비어 있습니다.");
		}
		if (cart.stream().anyMatch(Objects::isNull)) {
			throw new InvalidComparisonRequestException(Reason.INVALID_CART_LINE, "장바구니에 비어 있는 항목이 있습니다.");
		}
		if (!StringUtils.hasText(areaCode)) {
			throw new InvalidComparisonRequestException(Reason.REGION_REQUIRED, "비교할 지역을 선택해 주세요.");
		}
		for (CartLine line : cart) {
			if (line.quantity() < MIN_QUANTITY || line.quantity() > MAX_QUANTITY) {
				throw new InvalidComparisonRequestException(Reason.QUANTITY_OUT_OF_RANGE,
						"수량은 " + MIN_QUANTITY + "~" + MAX_QUANTITY + " 사이여야 합니다: 상품 " + line.goodId());
			}
		}
		Set<Long> seen = new HashSet<>();
		for (CartLine line : cart) {
			if (!seen.add(line.goodId())) {
				throw new InvalidComparisonRequestException(Reason.DUPLICATE_PRODUCT,
						"같은 상품이 여러 줄 있습니다: " + line.goodId());
			}
		}
	}

	private static ProductInfo toProductInfo(Product product) {
		return new ProductInfo(product.getGoodId(), product.getName(), product.getTotalCnt(), product.getTotalDivCode());
	}
}
