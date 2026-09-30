package com.martpick.publicdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.martpick.publicdata.PublicDataApiException.Type;

class PublicDataApiExceptionTest {

	@Test
	void 네트워크_오류와_타임아웃은_재시도_대상이다() {
		assertThat(new PublicDataApiException(Type.NETWORK, "op", "요청 실패 (SocketTimeoutException)").isRetryable()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(ints = {500, 502, 503, 504, 599})
	void HTTP_5xx는_재시도_대상이다(int status) {
		assertThat(new PublicDataApiException(Type.HTTP_ERROR, "op", "HTTP " + status, status).isRetryable()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(ints = {400, 401, 403, 404, 429})
	void HTTP_4xx와_429는_재시도하지_않는다(int status) {
		assertThat(new PublicDataApiException(Type.HTTP_ERROR, "op", "HTTP " + status, status).isRetryable()).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = Type.class, names = {"MISSING_SERVICE_KEY", "PARSE_ERROR", "API_ERROR", "UNEXPECTED_STRUCTURE",
			"INCOMPLETE_RESPONSE", "INVALID_ROW", "INCONSISTENT_ROW"})
	void 인증키_응답_형식_데이터_오류는_재시도하지_않는다(Type type) {
		assertThat(new PublicDataApiException(type, "op", "오류").isRetryable()).isFalse();
	}

	@Test
	void HTTP_상태를_알_수_없는_HTTP_오류는_재시도하지_않는다() {
		assertThat(new PublicDataApiException(Type.HTTP_ERROR, "op", "HTTP ?").isRetryable()).isFalse();
	}
}
