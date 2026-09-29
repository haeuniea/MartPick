package com.martpick.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.lang.reflect.RecordComponent;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.martpick.catalog.StorePriceRow;

class PriceComparatorTest {

	private static final ProductInfo RAMEN = new ProductInfo(1000L, "팔도 왕뚜껑(110g)", "110", "G");
	private static final ProductInfo SOUP = new ProductInfo(1003L, "비비고 사골곰탕(500g)", "500", "G");
	private static final ProductInfo RICE_SOUP = new ProductInfo(1008L, "햇반 컵반 미역국밥(167g)", "167", "G");
	private static final List<ProductInfo> PRODUCTS = List.of(RAMEN, SOUP, RICE_SOUP);

	private final PriceComparator comparator = new PriceComparator();

	/**
	 * 실제 사전 조회 가격(API_RESEARCH 8장, 2026-09-25). 세 판매점은 실제로는 서로 다른 지역이며,
	 * 계산 검증을 위해 한 지역의 조회 결과처럼 모은 조합이다.
	 * 검산: 89 = 1,150 + 1,480 + 3,990 = 6,620 / 887 = 1,280 + 1,980 + 4,980 = 8,240 / 765 = 1,390 + 2,180 + 4,980 = 8,550
	 */
	private static final List<StorePriceRow> SURVEYED_ROWS = List.of(
			row(887, 1000, 1280), row(887, 1003, 1980), row(887, 1008, 4980),
			row(89, 1000, 1150), row(89, 1003, 1480), row(89, 1008, 3990),
			row(765, 1000, 1390), row(765, 1003, 2180), row(765, 1008, 4980));

	@Test
	void 모든_상품_가격이_있는_판매점의_총가격을_계산한다() {
		ComparisonResult result = comparator.compare(cart(1, 1, 1), PRODUCTS, SURVEYED_ROWS);

		assertThat(result.completeStores()).extracting(CompleteStore::entpId, CompleteStore::totalPrice)
				.containsExactly(
						tuple(89L, 6_620L),
						tuple(887L, 8_240L),
						tuple(765L, 8_550L));
		assertThat(result.partialStores()).isEmpty();
	}

	@Test
	void 수량을_반영해_상품별_소계와_총가격을_계산한다() {
		ComparisonResult result = comparator.compare(cart(2, 1, 1), PRODUCTS, List.of(
				row(89, 1000, 1150), row(89, 1003, 1480), row(89, 1008, 3990)));

		CompleteStore store = result.completeStores().get(0);
		assertThat(store.items()).extracting(ItemPrice::product, ItemPrice::unitPrice, ItemPrice::quantity, ItemPrice::subtotal)
				.containsExactly(
						tuple(RAMEN, 1150, 2, 2_300L),
						tuple(SOUP, 1480, 1, 1_480L),
						tuple(RICE_SOUP, 3990, 1, 3_990L));
		assertThat(store.totalPrice()).isEqualTo(7_770L);
	}

	@Test
	void 일부_상품_가격이_없는_판매점은_확인된_상품의_소계만_합산한다() {
		ComparisonResult result = comparator.compare(cart(1, 2, 1), PRODUCTS, List.of(
				row(10, 1000, 3800), row(10, 1003, 2900)));

		PartialStore store = result.partialStores().get(0);
		assertThat(store.pricedItems()).extracting(ItemPrice::product, ItemPrice::subtotal)
				.containsExactly(
						tuple(RAMEN, 3_800L),
						tuple(SOUP, 5_800L));
		assertThat(store.knownSubtotal()).isEqualTo(9_600L);
		assertThat(result.completeStores()).isEmpty();
	}

	@Test
	void 가격이_누락된_상품_목록을_장바구니_순서대로_정확히_제공한다() {
		ComparisonResult result = comparator.compare(cart(1, 1, 1), PRODUCTS, List.of(row(10, 1003, 2900)));

		assertThat(result.partialStores().get(0).missingProducts()).containsExactly(RAMEN, RICE_SOUP);
	}

	@Test
	void 부분_비교_판매점에는_totalPrice가_없다() {
		assertThat(PartialStore.class.getRecordComponents())
				.extracting(RecordComponent::getName)
				.doesNotContain("totalPrice")
				.contains("knownSubtotal");
	}

	@Test
	void 가격이_누락된_판매점은_0원으로_합산되어_완전_비교에_들어가지_않는다() {
		ComparisonResult result = comparator.compare(cart(1, 1, 1), PRODUCTS, List.of(
				row(1, 1000, 100), row(1, 1003, 100),
				row(2, 1000, 1000), row(2, 1003, 1000), row(2, 1008, 1000)));

		assertThat(result.completeStores()).extracting(CompleteStore::entpId).containsExactly(2L);
		assertThat(result.partialStores()).extracting(PartialStore::entpId).containsExactly(1L);
	}

	@Test
	void 완전_비교_판매점은_총가격_오름차순으로_정렬한다() {
		ComparisonResult result = comparator.compare(cart(1, 1, 1), PRODUCTS, List.of(
				row(1, 1000, 3000), row(1, 1003, 3000), row(1, 1008, 3000),
				row(2, 1000, 1000), row(2, 1003, 1000), row(2, 1008, 1000),
				row(3, 1000, 2000), row(3, 1003, 2000), row(3, 1008, 2000)));

		assertThat(result.completeStores()).extracting(CompleteStore::entpId).containsExactly(2L, 3L, 1L);
	}

	@Test
	void 총가격이_같으면_판매점_ID_숫자_오름차순으로_정렬한다() {
		ComparisonResult result = comparator.compare(cart(1, 1, 1), PRODUCTS, List.of(
				row(5_499_990_000L, 1000, 1000), row(5_499_990_000L, 1003, 1000), row(5_499_990_000L, 1008, 1000),
				row(10, 1000, 1000), row(10, 1003, 1000), row(10, 1008, 1000),
				row(887, 1000, 1000), row(887, 1003, 1000), row(887, 1008, 1000),
				row(9, 1000, 1000), row(9, 1003, 1000), row(9, 1008, 1000)));

		assertThat(result.completeStores()).extracting(CompleteStore::entpId)
				.containsExactly(9L, 10L, 887L, 5_499_990_000L);
	}

	@Test
	void 부분_비교_판매점은_knownSubtotal과_관계없이_판매점_ID_오름차순으로_정렬한다() {
		ComparisonResult result = comparator.compare(cart(1, 1, 1), PRODUCTS, List.of(
				row(887, 1000, 9000),
				row(10, 1003, 100),
				row(9, 1008, 5000)));

		assertThat(result.partialStores()).extracting(PartialStore::entpId).containsExactly(9L, 10L, 887L);
	}

	@Test
	void 장바구니_상품_가격이_하나도_없는_판매점은_결과에서_제외한다() {
		ComparisonResult result = comparator.compare(List.of(new CartLine(1000L, 1)), List.of(RAMEN), List.of(
				row(1, 1000, 1280),
				row(2, 1003, 1980)));

		assertThat(result.completeStores()).extracting(CompleteStore::entpId).containsExactly(1L);
		assertThat(result.partialStores()).isEmpty();
	}

	@Test
	void 완전_비교_판매점이_있으면_COMPARED이다() {
		ComparisonResult result = comparator.compare(cart(1, 1, 1), PRODUCTS, SURVEYED_ROWS);

		assertThat(result.status()).isEqualTo(ComparisonStatus.COMPARED);
	}

	@Test
	void 부분_비교_판매점만_있으면_NO_COMPLETE_STORE이다() {
		ComparisonResult result = comparator.compare(cart(1, 1, 1), PRODUCTS, List.of(row(1, 1000, 1280)));

		assertThat(result.status()).isEqualTo(ComparisonStatus.NO_COMPLETE_STORE);
	}

	@Test
	void 가격이_확인된_판매점이_없으면_NO_PRICED_STORE이다() {
		ComparisonResult result = comparator.compare(cart(1, 1, 1), PRODUCTS, List.of());

		assertThat(result.completeStores()).isEmpty();
		assertThat(result.partialStores()).isEmpty();
		assertThat(result.status()).isEqualTo(ComparisonStatus.NO_PRICED_STORE);
	}

	@Test
	void 도로명_기본주소가_있으면_도로명_주소를_사용한다() {
		ComparisonResult result = comparator.compare(List.of(new CartLine(1000L, 1)), List.of(RAMEN), List.of(
				new StorePriceRow(786L, "이마트연수점", "인천광역시 연수구 경원대로 184", "(동춘동)",
						"인천광역시 연수구 동춘동  926-9 이마트", "이마트 연수점", 1000L, 1280)));

		assertThat(result.completeStores().get(0).address()).isEqualTo("인천광역시 연수구 경원대로 184 (동춘동)");
	}

	@Test
	void 도로명_기본주소가_없으면_지번_주소를_사용하고_공백과_null을_정리한다() {
		ComparisonResult result = comparator.compare(List.of(new CartLine(1000L, 1)), List.of(RAMEN), List.of(
				new StorePriceRow(89L, "GS더프레시울산구영점", null, "(무시되는 도로명 상세)",
						"울산 울주군 범서읍  구영리 ", "849-1", 1000L, 1150),
				new StorePriceRow(90L, "상세주소없음마트", "  ", null, "서울 도봉구", null, 1000L, 1200)));

		assertThat(result.completeStores()).extracting(CompleteStore::address)
				.containsExactly("울산 울주군 범서읍 구영리 849-1", "서울 도봉구");
	}

	@Test
	void 큰_가격과_수량도_long으로_정확히_계산한다() {
		ComparisonResult result = comparator.compare(cart(99, 99, 99), PRODUCTS, List.of(
				row(1, 1000, Integer.MAX_VALUE), row(1, 1003, Integer.MAX_VALUE), row(1, 1008, Integer.MAX_VALUE),
				row(2, 1000, Integer.MAX_VALUE), row(2, 1003, Integer.MAX_VALUE)));

		assertThat(result.completeStores().get(0).items().get(0).subtotal()).isEqualTo(212_600_881_053L);
		assertThat(result.completeStores().get(0).totalPrice()).isEqualTo(637_802_643_159L);
		assertThat(result.partialStores().get(0).knownSubtotal()).isEqualTo(425_201_762_106L);
	}

	@Test
	void 같은_판매점_상품의_가격_행이_중복되면_실패한다() {
		assertThatThrownBy(() -> comparator.compare(List.of(new CartLine(1000L, 1)), List.of(RAMEN), List.of(
				row(1, 1000, 1280), row(1, 1000, 1300))))
				.isInstanceOf(IllegalStateException.class);
	}

	private static List<CartLine> cart(int ramen, int soup, int riceSoup) {
		return List.of(new CartLine(1000L, ramen), new CartLine(1003L, soup), new CartLine(1008L, riceSoup));
	}

	private static StorePriceRow row(long entpId, long goodId, int price) {
		return new StorePriceRow(entpId, "판매점" + entpId, "도로명 " + entpId, null, "지번 " + entpId, null, goodId, price);
	}
}
