package com.martpick.publicdata;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 한국소비자원 생필품 가격 정보 API 클라이언트.
 * 정상 빈 결과는 빈 목록, 실패는 {@link PublicDataApiException}으로 구분한다.
 * 인증키가 들어간 요청 URI와 원본 예외 메시지는 로그·예외에 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicDataClient {

	private static final String REGION_OPERATION = "getStandardInfoSvc.do";
	private static final String STORE_OPERATION = "getStoreInfoSvc.do";
	private static final String PRODUCT_OPERATION = "getProductInfoSvc.do";
	private static final String PRICE_OPERATION = "getProductPriceInfoSvc";

	private static final String REGION_ROW = "iros.openapi.service.vo.stdInfoVO";
	private static final String STORE_ROW = "iros.openapi.service.vo.entpInfoVO";
	private static final String PRODUCT_ROW = "item";
	private static final String PRICE_ROW = "iros.openapi.service.vo.goodPriceVO";

	private static final DateTimeFormatter INSPECT_DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
	private static final String REDACTED = "[REDACTED]";

	private final RestClient restClient;
	private final PublicDataProperties properties;

	public List<RegionRow> fetchRegions() {
		return fetch(REGION_OPERATION, REGION_ROW, Map.of("classCode", "AR"), (row, index) -> new RegionRow(
				required(row, "code", index, REGION_OPERATION),
				required(row, "codeName", index, REGION_OPERATION),
				required(row, "highCode", index, REGION_OPERATION)));
	}

	public List<StoreRow> fetchStores() {
		return fetch(STORE_OPERATION, STORE_ROW, Map.of(), (row, index) -> new StoreRow(
				longValue(row, "entpId", index, STORE_OPERATION),
				required(row, "entpName", index, STORE_OPERATION),
				required(row, "entpTypeCode", index, STORE_OPERATION),
				required(row, "entpAreaCode", index, STORE_OPERATION),
				required(row, "areaDetailCode", index, STORE_OPERATION),
				optional(row, "roadAddrBasic", index, STORE_OPERATION),
				optional(row, "roadAddrDetail", index, STORE_OPERATION),
				required(row, "plmkAddrBasic", index, STORE_OPERATION),
				optional(row, "plmkAddrDetail", index, STORE_OPERATION)));
	}

	public List<ProductRow> fetchProducts() {
		return fetch(PRODUCT_OPERATION, PRODUCT_ROW, Map.of(), (row, index) -> new ProductRow(
				longValue(row, "goodId", index, PRODUCT_OPERATION),
				required(row, "goodName", index, PRODUCT_OPERATION),
				required(row, "goodTotalCnt", index, PRODUCT_OPERATION),
				required(row, "goodTotalDivCode", index, PRODUCT_OPERATION)));
	}

	public List<PriceRow> fetchPrices(LocalDate inspectDay, long goodId) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("goodInspectDay", inspectDay.format(INSPECT_DAY_FORMAT));
		params.put("goodId", String.valueOf(goodId));
		return fetch(PRICE_OPERATION, PRICE_ROW, params, (row, index) -> {
			PriceRow price = new PriceRow(
					inspectDay(row, index),
					longValue(row, "entpId", index, PRICE_OPERATION),
					longValue(row, "goodId", index, PRICE_OPERATION),
					positiveInt(row, "goodPrice", index, PRICE_OPERATION));
			if (!price.inspectDay().equals(inspectDay) || price.goodId() != goodId) {
				throw new PublicDataApiException(PublicDataApiException.Type.INCONSISTENT_ROW, PRICE_OPERATION,
						"행 " + index + "이 요청과 다릅니다: goodInspectDay=" + price.inspectDay() + ", goodId=" + price.goodId());
			}
			return price;
		});
	}

	private <T> List<T> fetch(String operation, String rowElementName, Map<String, String> params,
			BiFunction<Element, Integer, T> mapper) {
		String serviceKey = properties.serviceKey();
		if (!StringUtils.hasText(serviceKey)) {
			throw new PublicDataApiException(PublicDataApiException.Type.MISSING_SERVICE_KEY, operation,
					"인증키(DATA_GO_KR_SERVICE_KEY)가 설정되지 않았습니다.");
		}
		try {
			RawResponse response = request(operation, serviceKey, params);
			if (!response.isSuccessful()) {
				throw new PublicDataApiException(PublicDataApiException.Type.HTTP_ERROR, operation,
						"HTTP " + response.status(), response.status());
			}
			List<Element> rows = PublicDataXmlParser.parseRows(response.body(), operation, rowElementName);
			List<T> mapped = new ArrayList<>(rows.size());
			for (int i = 0; i < rows.size(); i++) {
				mapped.add(mapper.apply(rows.get(i), i));
			}
			log.info("공공데이터 API 호출 성공: {} {} rows={}", operation, params, mapped.size());
			return mapped;
		} catch (PublicDataApiException e) {
			throw redact(e, serviceKey);
		}
	}

	private RawResponse request(String operation, String serviceKey, Map<String, String> params) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
				.pathSegment(operation)
				.queryParam("serviceKey", serviceKey);
		params.forEach(builder::queryParam);
		URI uri = builder.encode().build().toUri();
		try {
			return restClient.get()
					.uri(uri)
					.exchange((request, response) -> new RawResponse(
							response.getStatusCode().value(), response.getBody().readAllBytes()));
		} catch (RestClientException e) {
			// 원본 예외 메시지에는 인증키가 포함된 요청 URI가 들어 있으므로 예외 종류만 남긴다.
			throw new PublicDataApiException(PublicDataApiException.Type.NETWORK, operation,
					"요청 실패 (" + rootCauseName(e) + ")");
		}
	}

	private static PublicDataApiException redact(PublicDataApiException e, String serviceKey) {
		if (!e.getDetail().contains(serviceKey)) {
			return e;
		}
		return new PublicDataApiException(e.getType(), e.getOperation(), e.getDetail().replace(serviceKey, REDACTED),
				e.getHttpStatus());
	}

	private static String rootCauseName(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root.getClass().getSimpleName();
	}

	private static String required(Element row, String field, int index, String operation) {
		String value = optional(row, field, index, operation);
		if (value == null) {
			throw new PublicDataApiException(PublicDataApiException.Type.INVALID_ROW, operation,
					"행 " + index + "의 필수 필드 " + field + "가 없거나 비어 있습니다.");
		}
		return value;
	}

	private static String optional(Element row, String field, int index, String operation) {
		Element element = null;
		NodeList children = row.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element child && child.getTagName().equals(field)) {
				if (element != null) {
					throw new PublicDataApiException(PublicDataApiException.Type.INVALID_ROW, operation,
							"행 " + index + "에 필드 " + field + "가 중복되었습니다.");
				}
				element = child;
			}
		}
		if (element == null) {
			return null;
		}
		String value = element.getTextContent().trim();
		return value.isEmpty() ? null : value;
	}

	private static long longValue(Element row, String field, int index, String operation) {
		String value = required(row, field, index, operation);
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw invalidFormat(field, value, index, operation);
		}
	}

	private static int positiveInt(Element row, String field, int index, String operation) {
		String value = required(row, field, index, operation);
		int number;
		try {
			number = Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw invalidFormat(field, value, index, operation);
		}
		if (number <= 0) {
			throw invalidFormat(field, value, index, operation);
		}
		return number;
	}

	private static LocalDate inspectDay(Element row, int index) {
		String value = required(row, "goodInspectDay", index, PRICE_OPERATION);
		try {
			return LocalDate.parse(value, INSPECT_DAY_FORMAT);
		} catch (DateTimeParseException e) {
			throw invalidFormat("goodInspectDay", value, index, PRICE_OPERATION);
		}
	}

	private static PublicDataApiException invalidFormat(String field, String value, int index, String operation) {
		return new PublicDataApiException(PublicDataApiException.Type.INVALID_ROW, operation,
				"행 " + index + "의 " + field + " 값 형식이 올바르지 않습니다: " + value);
	}

	private record RawResponse(int status, byte[] body) {

		boolean isSuccessful() {
			return status >= 200 && status < 300;
		}
	}
}
