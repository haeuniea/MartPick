package com.martpick.publicdata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 공공데이터 API 응답 XML에서 메타데이터(resultCode 등)와 데이터 행을 분리한다.
 * 확인된 구조(API_RESEARCH.md 1장)와 다른 요소는 무시하지 않고 실패로 처리한다.
 */
final class PublicDataXmlParser {

	private static final Set<String> PAGING_ELEMENTS = Set.of("totalCount", "numOfRows", "pageNo");
	private static final Set<String> ROOT_ELEMENTS = Set.of("result", "resultCode", "resultMsg",
			"totalCount", "numOfRows", "pageNo");

	private PublicDataXmlParser() {
	}

	static List<Element> parseRows(byte[] body, String operation, String rowElementName) {
		if (body == null || body.length == 0) {
			throw new PublicDataApiException(PublicDataApiException.Type.PARSE_ERROR, operation, "응답 본문이 비어 있습니다.");
		}
		Element root = parseDocument(body, operation).getDocumentElement();
		if (!"response".equals(root.getTagName())) {
			throw new PublicDataApiException(PublicDataApiException.Type.PARSE_ERROR, operation,
					"예상하지 못한 루트 요소: " + root.getTagName());
		}

		Map<String, Element> meta = rootChildren(root, operation);
		Element resultCode = meta.get("resultCode");
		if (resultCode == null) {
			throw new PublicDataApiException(PublicDataApiException.Type.PARSE_ERROR, operation, "resultCode가 없습니다.");
		}
		String code = resultCode.getTextContent().trim();
		if (!"00".equals(code)) {
			Element resultMsg = meta.get("resultMsg");
			String message = resultMsg == null ? "" : resultMsg.getTextContent().trim();
			throw new PublicDataApiException(PublicDataApiException.Type.API_ERROR, operation,
					"resultCode=" + code + ", resultMsg=" + message);
		}

		Element result = meta.get("result");
		if (result == null) {
			throw new PublicDataApiException(PublicDataApiException.Type.PARSE_ERROR, operation,
					"resultCode=00이지만 result 요소가 없습니다.");
		}
		List<Element> rows = dataRows(result, operation, rowElementName);
		verifyPaging(meta, rows.size(), operation);
		return rows;
	}

	private static Document parseDocument(byte[] body, String operation) {
		try {
			DocumentBuilder builder = secureFactory().newDocumentBuilder();
			builder.setErrorHandler(new DefaultHandler());
			return builder.parse(new ByteArrayInputStream(body));
		} catch (SAXException | IOException e) {
			throw new PublicDataApiException(PublicDataApiException.Type.PARSE_ERROR, operation,
					"XML 파싱 실패 (" + e.getClass().getSimpleName() + ")");
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("XML 파서 보안 설정을 적용할 수 없습니다.", e);
		}
	}

	private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory;
	}

	private static Map<String, Element> rootChildren(Element root, String operation) {
		Map<String, Element> children = new HashMap<>();
		NodeList nodes = root.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			rejectUnexpectedText(node, operation, "response");
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			String name = node.getNodeName();
			if (!ROOT_ELEMENTS.contains(name)) {
				throw new PublicDataApiException(PublicDataApiException.Type.UNEXPECTED_STRUCTURE, operation,
						"예상하지 못한 응답 요소: " + name);
			}
			if (children.put(name, (Element) node) != null) {
				throw new PublicDataApiException(PublicDataApiException.Type.UNEXPECTED_STRUCTURE, operation,
						"응답 요소가 중복되었습니다: " + name);
			}
		}
		return children;
	}

	private static List<Element> dataRows(Element result, String operation, String rowElementName) {
		List<Element> rows = new ArrayList<>();
		NodeList nodes = result.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			rejectUnexpectedText(node, operation, "result");
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			if (!rowElementName.equals(node.getNodeName())) {
				throw new PublicDataApiException(PublicDataApiException.Type.UNEXPECTED_STRUCTURE, operation,
						"예상하지 못한 행 요소: " + node.getNodeName());
			}
			rows.add((Element) node);
		}
		return rows;
	}

	private static void rejectUnexpectedText(Node node, String operation, String parent) {
		boolean text = node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE;
		if (text && !node.getTextContent().isBlank()) {
			throw new PublicDataApiException(PublicDataApiException.Type.UNEXPECTED_STRUCTURE, operation,
					parent + " 요소에 예상하지 못한 텍스트가 있습니다.");
		}
	}

	/**
	 * 현재 API는 페이징 메타데이터를 반환하지 않는다(API_RESEARCH.md 1장).
	 * 메타데이터가 나타나면 첫 페이지이고 totalCount와 반환 건수가 일치할 때만 완전한 응답으로 본다.
	 */
	private static void verifyPaging(Map<String, Element> meta, int rowCount, String operation) {
		if (PAGING_ELEMENTS.stream().noneMatch(meta::containsKey)) {
			return;
		}
		Element pageNo = meta.get("pageNo");
		if (pageNo != null && integer(pageNo, operation) != 1) {
			throw new PublicDataApiException(PublicDataApiException.Type.INCOMPLETE_RESPONSE, operation,
					"첫 페이지가 아닌 응답입니다: pageNo=" + pageNo.getTextContent().trim());
		}
		Element totalCount = meta.get("totalCount");
		if (totalCount == null) {
			throw new PublicDataApiException(PublicDataApiException.Type.INCOMPLETE_RESPONSE, operation,
					"페이지 정보는 있으나 totalCount가 없어 완전성을 판단할 수 없습니다.");
		}
		int total = integer(totalCount, operation);
		if (total != rowCount) {
			throw new PublicDataApiException(PublicDataApiException.Type.INCOMPLETE_RESPONSE, operation,
					"totalCount=" + total + "와 반환 건수 " + rowCount + "가 다릅니다. 추가 페이지 조회는 지원하지 않습니다.");
		}
	}

	private static int integer(Element element, String operation) {
		String value = element.getTextContent().trim();
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new PublicDataApiException(PublicDataApiException.Type.PARSE_ERROR, operation,
					element.getTagName() + " 값이 숫자가 아닙니다: " + value);
		}
	}
}
