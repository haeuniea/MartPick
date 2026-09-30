package com.martpick.publicdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@ExtendWith(OutputCaptureExtension.class)
class PublicDataClientTest {

	private static final String SERVICE_KEY = "FAKEKEY0123456789abcdefFAKEKEY";
	private static final String BASE_URL = "https://api.test/B551919/ProductPriceInfoService";
	private static final MediaType TEXT_HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");
	private static final LocalDate DAY = LocalDate.of(2026, 9, 25);

	private MockRestServiceServer server;
	private PublicDataClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new PublicDataClient(builder.build(), properties(SERVICE_KEY));
	}

	@Test
	void 지역코드는_classCode_AR로_요청하고_행을_변환한다() throws IOException {
		expect("/getStandardInfoSvc.do")
				.andExpect(queryParam("classCode", "AR"))
				.andRespond(withSuccess(fixture("region.xml"), TEXT_HTML_UTF8));

		List<RegionRow> rows = client.fetchRegions();

		server.verify();
		assertThat(rows).containsExactly(
				new RegionRow("020100000", "서울특별시", "020000000"),
				new RegionRow("020101000", "도봉구", "020100000"),
				new RegionRow("021800000", "전남광주통합특별시", "020000000"));
	}

	@Test
	void 판매점은_열자리_ID와_없는_도로명주소를_처리한다() throws IOException {
		expect("/getStoreInfoSvc.do").andRespond(withSuccess(fixture("store.xml"), TEXT_HTML_UTF8));

		List<StoreRow> rows = client.fetchStores();

		assertThat(rows).extracting(StoreRow::entpId).containsExactly(786L, 89L, 5_499_990_000L);
		StoreRow emart = rows.get(0);
		assertThat(emart.name()).isEqualTo("이마트연수점");
		assertThat(emart.typeCode()).isEqualTo("LM");
		assertThat(emart.areaCode()).isEqualTo("020700000");
		assertThat(emart.areaDetailCode()).isEqualTo("020710000");
		assertThat(emart.roadAddrBasic()).isEqualTo("인천광역시 연수구 경원대로 184");
		assertThat(emart.roadAddrDetail()).isEqualTo("(동춘동)");
		assertThat(emart.plmkAddrBasic()).isEqualTo("인천광역시 연수구 동춘동  926-9 이마트");
		StoreRow gs = rows.get(1);
		assertThat(gs.roadAddrBasic()).isNull();
		assertThat(gs.roadAddrDetail()).isNull();
		assertThat(gs.plmkAddrDetail()).isEqualTo("849-1");
	}

	@Test
	void 공백뿐인_선택_필드는_null로_변환한다() {
		expect("/getStoreInfoSvc.do").andRespond(withSuccess(storeXml("""
				<entpId>1</entpId><entpName>마트</entpName><entpTypeCode>SM</entpTypeCode>
				<entpAreaCode>020100000</entpAreaCode><areaDetailCode>020101000</areaDetailCode>
				<plmkAddrBasic>서울 도봉구</plmkAddrBasic><roadAddrBasic>   </roadAddrBasic>
				"""), TEXT_HTML_UTF8));

		assertThat(client.fetchStores().get(0).roadAddrBasic()).isNull();
	}

	@Test
	void 상품은_규격_필드를_원문대로_변환한다() throws IOException {
		expect("/getProductInfoSvc.do").andRespond(withSuccess(fixture("product.xml"), TEXT_HTML_UTF8));

		List<ProductRow> rows = client.fetchProducts();

		assertThat(rows).containsExactly(
				new ProductRow(104L, "도브 센서티브 뷰티바(4개)", "4", "EA"),
				new ProductRow(1000L, "팔도 왕뚜껑(110g)", "110", "G"));
	}

	@Test
	void 가격은_do가_없는_경로에_조사일과_상품ID로_요청하고_행을_변환한다() throws IOException {
		expect("/getProductPriceInfoSvc")
				.andExpect(queryParam("goodInspectDay", "20260925"))
				.andExpect(queryParam("goodId", "1000"))
				.andRespond(withSuccess(fixture("price.xml"), TEXT_HTML_UTF8));

		List<PriceRow> rows = client.fetchPrices(DAY, 1000L);

		server.verify();
		assertThat(rows).containsExactly(
				new PriceRow(DAY, 887L, 1000L, 1280),
				new PriceRow(DAY, 89L, 1000L, 1150),
				new PriceRow(DAY, 5_499_990_000L, 1000L, 1280),
				new PriceRow(DAY, 682L, 1000L, 1500));
	}

	@Test
	void 정상_빈_결과는_빈_목록을_반환한다() {
		expect("/getProductPriceInfoSvc").andRespond(withSuccess(
				"<response><result/><resultCode>00</resultCode><resultMsg>ok</resultMsg></response>", TEXT_HTML_UTF8));

		assertThat(client.fetchPrices(DAY, 1000L)).isEmpty();
	}

	@Test
	void 가격_응답에_요청하지_않은_상품ID가_있으면_실패한다() {
		expect("/getProductPriceInfoSvc").andRespond(withSuccess(priceXml("20260925", "1003", "1480"), TEXT_HTML_UTF8));

		assertFailure(() -> client.fetchPrices(DAY, 1000L), PublicDataApiException.Type.INCONSISTENT_ROW);
	}

	@Test
	void 가격_응답에_다른_조사일이_있으면_실패한다() {
		expect("/getProductPriceInfoSvc").andRespond(withSuccess(priceXml("20260918", "1000", "1280"), TEXT_HTML_UTF8));

		assertFailure(() -> client.fetchPrices(DAY, 1000L), PublicDataApiException.Type.INCONSISTENT_ROW);
	}

	@Test
	void 필수_필드가_없는_행이_하나라도_있으면_전체가_실패한다() {
		expect("/getStoreInfoSvc.do").andRespond(withSuccess(storeXml("""
				<entpId>1</entpId><entpTypeCode>SM</entpTypeCode>
				<entpAreaCode>020100000</entpAreaCode><areaDetailCode>020101000</areaDetailCode>
				<plmkAddrBasic>서울 도봉구</plmkAddrBasic>
				"""), TEXT_HTML_UTF8));

		assertThatThrownBy(() -> client.fetchStores())
				.isInstanceOfSatisfying(PublicDataApiException.class, e -> {
					assertThat(e.getType()).isEqualTo(PublicDataApiException.Type.INVALID_ROW);
					assertThat(e.getMessage()).contains("entpName");
				});
	}

	@Test
	void 숫자_형식이_잘못된_가격은_실패한다() {
		expect("/getProductPriceInfoSvc").andRespond(withSuccess(priceXml("20260925", "1000", "1,280"), TEXT_HTML_UTF8));

		assertFailure(() -> client.fetchPrices(DAY, 1000L), PublicDataApiException.Type.INVALID_ROW);
	}

	@Test
	void 가격이_0원이면_실패한다() {
		expect("/getProductPriceInfoSvc").andRespond(withSuccess(priceXml("20260925", "1000", "0"), TEXT_HTML_UTF8));

		assertFailure(() -> client.fetchPrices(DAY, 1000L), PublicDataApiException.Type.INVALID_ROW);
	}

	@Test
	void 같은_필드가_한_행에_두_번_있으면_실패한다() {
		expect("/getProductInfoSvc.do").andRespond(withSuccess("""
				<response><result><item>
				  <goodId>1000</goodId><goodId>1001</goodId><goodName>라면</goodName>
				  <goodTotalCnt>110</goodTotalCnt><goodTotalDivCode>G</goodTotalDivCode>
				</item></result><resultCode>00</resultCode></response>
				""", TEXT_HTML_UTF8));

		assertFailure(() -> client.fetchProducts(), PublicDataApiException.Type.INVALID_ROW);
	}

	@Test
	void resultCode가_00이_아니면_API_ERROR로_실패한다() {
		expect("/getStandardInfoSvc.do").andRespond(withSuccess(
				"<response><resultCode>01</resultCode><resultMsg>classCode 값이 없습니다.</resultMsg></response>",
				TEXT_HTML_UTF8));

		assertFailure(() -> client.fetchRegions(), PublicDataApiException.Type.API_ERROR);
	}

	@ParameterizedTest
	@ValueSource(ints = {400, 401, 500})
	void HTTP_오류_상태는_HTTP_ERROR로_실패한다(int status) {
		expect("/getProductInfoSvc.do").andRespond(withStatus(HttpStatus.valueOf(status)));

		assertThatThrownBy(() -> client.fetchProducts())
				.isInstanceOfSatisfying(PublicDataApiException.class, e -> {
					assertThat(e.getType()).isEqualTo(PublicDataApiException.Type.HTTP_ERROR);
					assertThat(e.getMessage()).contains(String.valueOf(status));
				});
	}

	@ParameterizedTest
	@ValueSource(ints = {500, 503})
	void HTTP_5xx_실패는_재시도_대상으로_분류된다(int status) {
		expect("/getProductInfoSvc.do").andRespond(withStatus(HttpStatus.valueOf(status)));

		assertThatThrownBy(() -> client.fetchProducts())
				.isInstanceOfSatisfying(PublicDataApiException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(status);
					assertThat(e.isRetryable()).isTrue();
				});
	}

	@ParameterizedTest
	@ValueSource(ints = {400, 401, 429})
	void HTTP_4xx와_429_실패는_재시도_대상이_아니다(int status) {
		expect("/getProductInfoSvc.do").andRespond(withStatus(HttpStatus.valueOf(status)));

		assertThatThrownBy(() -> client.fetchProducts())
				.isInstanceOfSatisfying(PublicDataApiException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(status);
					assertThat(e.isRetryable()).isFalse();
				});
	}

	@Test
	void 연결_실패와_타임아웃은_NETWORK로_실패한다() {
		expect("/getProductInfoSvc.do").andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertFailure(() -> client.fetchProducts(), PublicDataApiException.Type.NETWORK);
	}

	@Test
	void 타임아웃은_재시도_대상으로_분류된다() {
		expect("/getProductInfoSvc.do").andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertThat(catchFailure(() -> client.fetchProducts()).isRetryable()).isTrue();
	}

	@Test
	void resultCode가_00이_아닌_응답은_재시도_대상이_아니다() {
		expect("/getProductInfoSvc.do").andRespond(withSuccess("""
				<?xml version="1.0" encoding="UTF-8"?>
				<response><result/><resultCode>30</resultCode><resultMsg>SERVICE KEY IS NOT REGISTERED ERROR.</resultMsg></response>
				""", TEXT_HTML_UTF8));

		PublicDataApiException failure = catchFailure(() -> client.fetchProducts());

		assertThat(failure.getType()).isEqualTo(PublicDataApiException.Type.API_ERROR);
		assertThat(failure.isRetryable()).isFalse();
	}

	@Test
	void 인증키가_없으면_요청을_보내지_않고_실패한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer noRequestServer = MockRestServiceServer.bindTo(builder).build();
		PublicDataClient noKeyClient = new PublicDataClient(builder.build(), properties(" "));

		assertFailure(() -> noKeyClient.fetchProducts(), PublicDataApiException.Type.MISSING_SERVICE_KEY);
		noRequestServer.verify();
	}

	@Test
	void 네트워크_실패의_예외_메시지와_스택트레이스에_인증키가_없다() {
		expect("/getProductInfoSvc.do").andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertNoServiceKeyExposed(catchFailure(() -> client.fetchProducts()));
	}

	@Test
	void HTTP_실패의_예외_메시지와_스택트레이스에_인증키가_없다() {
		expect("/getProductInfoSvc.do").andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertNoServiceKeyExposed(catchFailure(() -> client.fetchProducts()));
	}

	@Test
	void 응답_메시지에_인증키가_포함되어도_예외에는_가려진다() {
		expect("/getStandardInfoSvc.do").andRespond(withSuccess(
				"<response><resultCode>30</resultCode><resultMsg>key " + SERVICE_KEY + " is not registered</resultMsg></response>",
				TEXT_HTML_UTF8));

		PublicDataApiException e = catchFailure(() -> client.fetchRegions());

		assertNoServiceKeyExposed(e);
		assertThat(e.getMessage()).contains("30");
	}

	@Test
	void 디버그_로그를_켜도_요청_로그에_인증키가_없다(CapturedOutput output) throws IOException {
		Logger springLogger = (Logger) LoggerFactory.getLogger("org.springframework");
		Logger appLogger = (Logger) LoggerFactory.getLogger("com.martpick");
		Level springLevel = springLogger.getLevel();
		Level appLevel = appLogger.getLevel();
		springLogger.setLevel(Level.DEBUG);
		appLogger.setLevel(Level.DEBUG);
		try {
			expect("/getProductPriceInfoSvc").andRespond(withSuccess(fixture("price.xml"), TEXT_HTML_UTF8));
			expect("/getProductInfoSvc.do").andRespond(withException(new SocketTimeoutException("Read timed out")));

			client.fetchPrices(DAY, 1000L);
			catchFailure(() -> client.fetchProducts());
		} finally {
			springLogger.setLevel(springLevel);
			appLogger.setLevel(appLevel);
		}

		assertThat(output.getAll()).contains("getProductPriceInfoSvc").doesNotContain(SERVICE_KEY);
	}

	private ResponseActions expect(String path) {
		return server.expect(request -> {
					assertThat(request.getURI().getPath()).isEqualTo("/B551919/ProductPriceInfoService" + path);
					assertThat(request.getURI().getQuery()).doesNotContain("numOfRows").doesNotContain("pageNo");
				})
				.andExpect(method(HttpMethod.GET))
				.andExpect(queryParam("serviceKey", SERVICE_KEY));
	}

	private static PublicDataProperties properties(String serviceKey) {
		return new PublicDataProperties(BASE_URL, serviceKey, Duration.ofSeconds(5), Duration.ofSeconds(30));
	}

	private static byte[] fixture(String name) throws IOException {
		return new ClassPathResource("publicdata/" + name).getContentAsByteArray();
	}

	private static String storeXml(String fields) {
		return "<response><result><iros.openapi.service.vo.entpInfoVO>" + fields
				+ "</iros.openapi.service.vo.entpInfoVO></result><resultCode>00</resultCode></response>";
	}

	private static String priceXml(String day, String goodId, String price) {
		return "<response><result><iros.openapi.service.vo.goodPriceVO>"
				+ "<goodInspectDay>" + day + "</goodInspectDay><entpId>887</entpId>"
				+ "<goodId>" + goodId + "</goodId><goodPrice>" + price + "</goodPrice>"
				+ "</iros.openapi.service.vo.goodPriceVO></result><resultCode>00</resultCode></response>";
	}

	private static PublicDataApiException catchFailure(Runnable call) {
		try {
			call.run();
		} catch (PublicDataApiException e) {
			return e;
		}
		throw new AssertionError("PublicDataApiException이 발생하지 않았습니다.");
	}

	private static void assertFailure(Runnable call, PublicDataApiException.Type type) {
		assertThat(catchFailure(call).getType()).isEqualTo(type);
	}

	private static void assertNoServiceKeyExposed(PublicDataApiException e) {
		StringWriter trace = new StringWriter();
		e.printStackTrace(new PrintWriter(trace));
		assertThat(e.getMessage()).doesNotContain(SERVICE_KEY);
		assertThat(e.toString()).doesNotContain(SERVICE_KEY);
		assertThat(trace.toString()).doesNotContain(SERVICE_KEY);
		for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
			assertThat(String.valueOf(cause.getMessage())).doesNotContain(SERVICE_KEY);
		}
	}
}
