package com.martpick.testsupport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테이블을 통째로 비우는 테스트가 개발 DB를 지우지 않도록, 삭제 전에 실제 연결 대상이 테스트 DB인지 확인한다.
 * 접속 URL은 환경변수나 시스템 속성으로 바뀔 수 있으므로 설정값을 믿지 않고, 연결된 서버가 알려주는 DB 이름과
 * 드라이버가 보고하는 URL의 DB 이름이 모두 {@value #TEST_DATABASE}와 정확히 같을 때만 통과시킨다.
 * 확인할 수 없는 경우에도 삭제하지 않고 실패한다.
 */
public final class TestDatabaseGuard {

	/** compose의 init 스크립트(docker/postgres/init)가 만드는 테스트 전용 DB 이름 */
	public static final String TEST_DATABASE = "martpick_test";

	private static final Pattern URL_DATABASE = Pattern.compile("jdbc:postgresql://[^/?#]+/([^/?#]+)(?:\\?.*)?");

	private TestDatabaseGuard() {
	}

	/**
	 * 연결 대상이 테스트 DB임을 확인한 뒤에만 지정한 테이블을 주어진 순서대로 비운다.
	 *
	 * @throws IllegalStateException 테스트 DB가 아니거나 연결 대상을 확인할 수 없는 경우 (아무것도 삭제하지 않음)
	 */
	public static void deleteAllRows(JdbcTemplate jdbc, String... tables) {
		requireTestDatabase(jdbc);
		for (String table : tables) {
			jdbc.update("delete from " + table);
		}
	}

	public static void requireTestDatabase(JdbcTemplate jdbc) {
		String serverDatabase;
		String driverUrl;
		try {
			serverDatabase = jdbc.queryForObject("select current_database()", String.class);
			driverUrl = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
		} catch (DataAccessException e) {
			throw new IllegalStateException("연결 대상을 확인할 수 없어 테이블을 비우지 않습니다.", e);
		}
		String urlDatabase = databaseOf(driverUrl);
		if (!TEST_DATABASE.equals(serverDatabase) || !TEST_DATABASE.equals(urlDatabase)) {
			throw new IllegalStateException("테스트 DB(" + TEST_DATABASE + ")가 아닌 곳에 연결되어 있어 테이블을 비우지 않습니다. "
					+ "서버가 알려준 DB 이름=" + serverDatabase + ", URL의 DB 이름=" + urlDatabase);
		}
	}

	private static String databaseOf(String jdbcUrl) {
		if (jdbcUrl == null) {
			return null;
		}
		Matcher matcher = URL_DATABASE.matcher(jdbcUrl);
		return matcher.matches() ? matcher.group(1) : null;
	}
}
