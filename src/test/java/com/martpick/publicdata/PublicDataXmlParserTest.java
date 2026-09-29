package com.martpick.publicdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

class PublicDataXmlParserTest {

	private static final String OPERATION = "getStoreInfoSvc.do";
	private static final String STORE_ROW = "iros.openapi.service.vo.entpInfoVO";

	@Test
	void API별_행_요소만_데이터_행으로_반환한다() {
		List<Element> rows = parse("""
				<response>
				  <result>
				    <iros.openapi.service.vo.entpInfoVO><entpId>1</entpId></iros.openapi.service.vo.entpInfoVO>
				    <iros.openapi.service.vo.entpInfoVO><entpId>2</entpId></iros.openapi.service.vo.entpInfoVO>
				  </result>
				  <resultCode>00</resultCode>
				  <resultMsg>ok</resultMsg>
				</response>
				""");

		assertThat(rows).hasSize(2);
		assertThat(rows).allMatch(row -> row.getTagName().equals(STORE_ROW));
	}

	@Test
	void 빈_result는_빈_목록이다() {
		List<Element> rows = parse("""
				<response><result/><resultCode>00</resultCode><resultMsg>ok</resultMsg></response>
				""");

		assertThat(rows).isEmpty();
	}

	@Test
	void result_아래에_다른_API의_행_요소가_있으면_실패한다() {
		assertFailure("""
				<response>
				  <result><item><goodId>1</goodId></item></result>
				  <resultCode>00</resultCode>
				</response>
				""", PublicDataApiException.Type.UNEXPECTED_STRUCTURE);
	}

	@Test
	void result_아래에_텍스트가_있으면_실패한다() {
		assertFailure("""
				<response><result>unexpected</result><resultCode>00</resultCode></response>
				""", PublicDataApiException.Type.UNEXPECTED_STRUCTURE);
	}

	@Test
	void 루트_아래에_알_수_없는_요소가_있으면_실패한다() {
		assertFailure("""
				<response><header/><result/><resultCode>00</resultCode></response>
				""", PublicDataApiException.Type.UNEXPECTED_STRUCTURE);
	}

	@Test
	void resultCode가_00이_아니면_코드와_메시지를_담아_실패한다() {
		assertThatThrownBy(() -> parse("""
				<response><resultCode>05</resultCode><resultMsg>entpId goodId 에 값이 모두 없습니다.</resultMsg></response>
				"""))
				.isInstanceOfSatisfying(PublicDataApiException.class, e -> {
					assertThat(e.getType()).isEqualTo(PublicDataApiException.Type.API_ERROR);
					assertThat(e.getMessage()).contains("05").contains("entpId goodId 에 값이 모두 없습니다.");
				});
	}

	@Test
	void resultCode가_없으면_실패한다() {
		assertFailure("<response><result/></response>", PublicDataApiException.Type.PARSE_ERROR);
	}

	@Test
	void resultCode가_00인데_result가_없으면_실패한다() {
		assertFailure("<response><resultCode>00</resultCode></response>", PublicDataApiException.Type.PARSE_ERROR);
	}

	@Test
	void 루트가_response가_아니면_실패한다() {
		assertFailure("""
				<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg></cmmMsgHeader></OpenAPI_ServiceResponse>
				""", PublicDataApiException.Type.PARSE_ERROR);
	}

	@Test
	void 빈_본문은_실패한다() {
		assertFailure("", PublicDataApiException.Type.PARSE_ERROR);
	}

	@Test
	void XML_형식이_아니면_실패한다() {
		assertFailure("<html><body>error", PublicDataApiException.Type.PARSE_ERROR);
	}

	@Test
	void DOCTYPE과_외부_엔티티는_거부하고_파일을_읽지_않는다(@TempDir Path dir) throws Exception {
		Path secret = dir.resolve("secret.txt");
		Files.writeString(secret, "TOP-SECRET-CONTENT");
		String xml = "<?xml version=\"1.0\"?><!DOCTYPE response [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
				+ "<response><result/><resultCode>00</resultCode><resultMsg>&xxe;</resultMsg></response>";

		assertThatThrownBy(() -> parse(xml))
				.isInstanceOfSatisfying(PublicDataApiException.class, e -> {
					assertThat(e.getType()).isEqualTo(PublicDataApiException.Type.PARSE_ERROR);
					assertThat(e.getMessage()).doesNotContain("TOP-SECRET-CONTENT");
				});
	}

	@Test
	void totalCount가_반환_건수와_같으면_정상이다() {
		List<Element> rows = parse("""
				<response>
				  <result><iros.openapi.service.vo.entpInfoVO><entpId>1</entpId></iros.openapi.service.vo.entpInfoVO></result>
				  <resultCode>00</resultCode><totalCount>1</totalCount><numOfRows>10</numOfRows><pageNo>1</pageNo>
				</response>
				""");

		assertThat(rows).hasSize(1);
	}

	@Test
	void totalCount보다_반환_건수가_적으면_추가_페이지가_필요하므로_실패한다() {
		assertFailure("""
				<response>
				  <result><iros.openapi.service.vo.entpInfoVO><entpId>1</entpId></iros.openapi.service.vo.entpInfoVO></result>
				  <resultCode>00</resultCode><totalCount>630</totalCount>
				</response>
				""", PublicDataApiException.Type.INCOMPLETE_RESPONSE);
	}

	@Test
	void totalCount보다_반환_건수가_많으면_실패한다() {
		assertFailure("""
				<response>
				  <result>
				    <iros.openapi.service.vo.entpInfoVO><entpId>1</entpId></iros.openapi.service.vo.entpInfoVO>
				    <iros.openapi.service.vo.entpInfoVO><entpId>2</entpId></iros.openapi.service.vo.entpInfoVO>
				  </result>
				  <resultCode>00</resultCode><totalCount>1</totalCount>
				</response>
				""", PublicDataApiException.Type.INCOMPLETE_RESPONSE);
	}

	@Test
	void 페이지_정보만_있고_totalCount가_없으면_완전성을_판단할_수_없어_실패한다() {
		assertFailure("""
				<response><result/><resultCode>00</resultCode><numOfRows>10</numOfRows><pageNo>1</pageNo></response>
				""", PublicDataApiException.Type.INCOMPLETE_RESPONSE);
	}

	@Test
	void 첫_페이지가_아니면_실패한다() {
		assertFailure("""
				<response><result/><resultCode>00</resultCode><totalCount>0</totalCount><pageNo>2</pageNo></response>
				""", PublicDataApiException.Type.INCOMPLETE_RESPONSE);
	}

	@Test
	void totalCount가_숫자가_아니면_실패한다() {
		assertFailure("""
				<response><result/><resultCode>00</resultCode><totalCount>many</totalCount></response>
				""", PublicDataApiException.Type.PARSE_ERROR);
	}

	private List<Element> parse(String xml) {
		return PublicDataXmlParser.parseRows(xml.getBytes(StandardCharsets.UTF_8), OPERATION, STORE_ROW);
	}

	private void assertFailure(String xml, PublicDataApiException.Type type) {
		assertThatThrownBy(() -> parse(xml))
				.isInstanceOfSatisfying(PublicDataApiException.class, e -> {
					assertThat(e.getType()).isEqualTo(type);
					assertThat(e.getOperation()).isEqualTo(OPERATION);
				});
	}
}
