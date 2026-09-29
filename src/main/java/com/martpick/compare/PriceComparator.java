package com.martpick.compare;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.martpick.catalog.StorePriceRow;

/**
 * 판매점별 장바구니 가격을 계산해 완전 비교와 부분 비교로 나눈다 (PROJECT_SPEC R3~R6).
 * 입력 가격 행은 이미 기준 조사일·지역·본사 제외 조건으로 조회된 것이어야 한다.
 */
@Component
public class PriceComparator {

	private static final Comparator<CompleteStore> COMPLETE_ORDER =
			Comparator.comparingLong(CompleteStore::totalPrice).thenComparingLong(CompleteStore::entpId);

	public ComparisonResult compare(List<CartLine> cart, List<ProductInfo> products, List<StorePriceRow> rows) {
		Map<Long, ProductInfo> productById = products.stream()
				.collect(Collectors.toMap(ProductInfo::goodId, Function.identity()));
		Set<Long> cartGoodIds = cart.stream().map(CartLine::goodId).collect(Collectors.toSet());

		List<CompleteStore> completeStores = new ArrayList<>();
		List<PartialStore> partialStores = new ArrayList<>();
		for (StorePrices store : groupByStore(rows, cartGoodIds).values()) {
			List<ItemPrice> pricedItems = new ArrayList<>();
			List<ProductInfo> missingProducts = new ArrayList<>();
			for (CartLine line : cart) {
				ProductInfo product = productById.get(line.goodId());
				if (product == null) {
					throw new IllegalArgumentException("상품 정보가 없는 장바구니 상품: " + line.goodId());
				}
				Integer unitPrice = store.prices().get(line.goodId());
				if (unitPrice == null) {
					missingProducts.add(product);
				} else {
					long subtotal = Math.multiplyExact((long) unitPrice, line.quantity());
					pricedItems.add(new ItemPrice(product, unitPrice, line.quantity(), subtotal));
				}
			}
			if (pricedItems.isEmpty()) {
				continue;
			}
			long sum = pricedItems.stream().mapToLong(ItemPrice::subtotal).reduce(0L, Math::addExact);
			StorePriceRow info = store.info();
			if (missingProducts.isEmpty()) {
				completeStores.add(new CompleteStore(info.entpId(), info.storeName(), address(info), pricedItems, sum));
			} else {
				partialStores.add(new PartialStore(info.entpId(), info.storeName(), address(info), pricedItems,
						missingProducts, sum));
			}
		}

		completeStores.sort(COMPLETE_ORDER);
		partialStores.sort(Comparator.comparingLong(PartialStore::entpId));
		return new ComparisonResult(List.copyOf(completeStores), List.copyOf(partialStores));
	}

	private static Map<Long, StorePrices> groupByStore(List<StorePriceRow> rows, Set<Long> cartGoodIds) {
		Map<Long, StorePrices> stores = new LinkedHashMap<>();
		for (StorePriceRow row : rows) {
			if (!cartGoodIds.contains(row.goodId())) {
				continue;
			}
			StorePrices store = stores.computeIfAbsent(row.entpId(), id -> new StorePrices(row, new HashMap<>()));
			if (store.prices().put(row.goodId(), row.price()) != null) {
				throw new IllegalStateException(
						"같은 판매점·상품의 가격 행이 중복되었습니다: entpId=" + row.entpId() + ", goodId=" + row.goodId());
			}
		}
		return stores;
	}

	/**
	 * 도로명 기본 주소가 있으면 도로명 기본·상세 주소, 없으면 지번 기본·상세 주소를 쓴다 (PROJECT_SPEC 2-3).
	 */
	private static String address(StorePriceRow row) {
		if (StringUtils.hasText(row.roadAddrBasic())) {
			return joinAddress(row.roadAddrBasic(), row.roadAddrDetail());
		}
		return joinAddress(row.plmkAddrBasic(), row.plmkAddrDetail());
	}

	private static String joinAddress(String basic, String detail) {
		return Stream.of(basic, detail)
				.filter(StringUtils::hasText)
				.map(part -> part.trim().replaceAll("\\s+", " "))
				.collect(Collectors.joining(" "));
	}

	private record StorePrices(StorePriceRow info, Map<Long, Integer> prices) {
	}
}
