package com.martpick.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테이블을 비우는 테스트가 개발 DB를 지우지 않도록, 삭제 전에 연결 대상을 확인하는 안전장치를 검증한다.
 * 실제 DB에 연결하지 않고 {@link JdbcTemplate}을 대체해, 잘못된 연결 대상에서 삭제 SQL이 한 번도 실행되지 않는지 확인한다.
 */
class TestDatabaseGuardTest {

	private static final String TEST_URL = "jdbc:postgresql://localhost:5432/martpick_test";
	private static final String DEV_URL = "jdbc:postgresql://localhost:5432/martpick";
	private static final String[] TABLES = {"collection_run", "cart_item", "member"};

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	@SuppressWarnings("unchecked")
	private void givenConnection(String serverDatabase, String driverUrl) {
		given(jdbc.queryForObject("select current_database()", String.class)).willReturn(serverDatabase);
		given(jdbc.execute(any(ConnectionCallback.class))).willReturn(driverUrl);
	}

	@Test
	void 서버와_URL이_모두_테스트_DB이면_지정한_순서대로_테이블을_비운다() {
		givenConnection("martpick_test", TEST_URL);

		TestDatabaseGuard.deleteAllRows(jdbc, TABLES);

		InOrder order = inOrder(jdbc);
		order.verify(jdbc).update("delete from collection_run");
		order.verify(jdbc).update("delete from cart_item");
		order.verify(jdbc).update("delete from member");
	}

	@Test
	void 쿼리_파라미터가_붙은_테스트_DB_URL도_허용한다() {
		givenConnection("martpick_test", TEST_URL + "?currentSchema=public&ssl=false");

		TestDatabaseGuard.deleteAllRows(jdbc, TABLES);

		verify(jdbc, times(3)).update(anyString());
	}

	@Test
	void 서버가_개발_DB라고_알려주면_삭제하지_않고_실패한다() {
		givenConnection("martpick", DEV_URL);

		assertThatThrownBy(() -> TestDatabaseGuard.deleteAllRows(jdbc, TABLES))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("martpick_test");

		verify(jdbc, never()).update(anyString());
	}

	@Test
	void URL은_테스트_DB인데_서버가_다른_DB라고_알려줘도_삭제하지_않는다() {
		givenConnection("martpick", TEST_URL);

		assertThatThrownBy(() -> TestDatabaseGuard.deleteAllRows(jdbc, TABLES))
				.isInstanceOf(IllegalStateException.class);

		verify(jdbc, never()).update(anyString());
	}

	@Test
	void 서버는_테스트_DB인데_URL이_개발_DB를_가리키면_삭제하지_않는다() {
		givenConnection("martpick_test", DEV_URL);

		assertThatThrownBy(() -> TestDatabaseGuard.deleteAllRows(jdbc, TABLES))
				.isInstanceOf(IllegalStateException.class);

		verify(jdbc, never()).update(anyString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"martpick_test_old", "martpick_testx", "xmartpick_test", "MARTPICK_TEST", "martpick_pre_full_20261001",
			"postgres", ""})
	void 이름이_비슷해도_정확히_일치하지_않으면_삭제하지_않는다(String name) {
		givenConnection(name, "jdbc:postgresql://localhost:5432/" + name);

		assertThatThrownBy(() -> TestDatabaseGuard.deleteAllRows(jdbc, TABLES))
				.isInstanceOf(IllegalStateException.class);

		verify(jdbc, never()).update(anyString());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "not-a-url", "jdbc:h2:mem:martpick_test", "jdbc:postgresql://localhost:5432/",
			"jdbc:postgresql://localhost:5432", "jdbc:postgresql://localhost:5432/martpick?x=/martpick_test",
			"jdbc:postgresql://localhost:5432/martpick_test/../martpick", "jdbc:postgresql://localhost:5432/martpick#/martpick_test"})
	void URL을_확인할_수_없거나_형식이_이상하면_삭제하지_않는다(String url) {
		givenConnection("martpick_test", url);

		assertThatThrownBy(() -> TestDatabaseGuard.deleteAllRows(jdbc, TABLES))
				.isInstanceOf(IllegalStateException.class);

		verify(jdbc, never()).update(anyString());
	}

	@Test
	void 서버가_DB_이름을_돌려주지_않으면_삭제하지_않는다() {
		givenConnection(null, TEST_URL);

		assertThatThrownBy(() -> TestDatabaseGuard.deleteAllRows(jdbc, TABLES))
				.isInstanceOf(IllegalStateException.class);

		verify(jdbc, never()).update(anyString());
	}

	@Test
	void 연결_대상을_조회하다_실패하면_삭제하지_않고_원인을_남긴다() {
		CannotGetJdbcConnectionException cause = new CannotGetJdbcConnectionException("연결 실패", new SQLException("down"));
		given(jdbc.queryForObject("select current_database()", String.class)).willThrow(cause);

		assertThatThrownBy(() -> TestDatabaseGuard.deleteAllRows(jdbc, TABLES))
				.isInstanceOf(IllegalStateException.class)
				.hasCause(cause);

		verify(jdbc, never()).update(anyString());
	}

	@Test
	void 실패_메시지에_접속_URL_전체를_담지_않는다() {
		givenConnection("martpick", "jdbc:postgresql://localhost:5432/martpick?user=u&password=secret");

		assertThatThrownBy(() -> TestDatabaseGuard.deleteAllRows(jdbc, TABLES))
				.isInstanceOf(IllegalStateException.class)
				.satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret").doesNotContain("password"));
	}

	@Test
	void 검증만_하는_호출도_잘못된_대상이면_실패하고_삭제는_하지_않는다() {
		givenConnection("martpick", DEV_URL);

		assertThatThrownBy(() -> TestDatabaseGuard.requireTestDatabase(jdbc)).isInstanceOf(IllegalStateException.class);

		verify(jdbc, never()).update(anyString());
	}
}
