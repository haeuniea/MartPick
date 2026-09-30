package com.martpick.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 수집 전용 실행(웹 서버 없는 모드, spring.main.web-application-type=none)에서 앱이 시작되는지 확인한다 (PROJECT_SPEC 4-1).
 * 테스트 DB(martpick_test)만 사용하고, 수집기는 Mock으로 대체해 API를 호출하지 않는다.
 */
class NonWebCollectRunTest {

	@Nested
	@SpringBootTest(webEnvironment = WebEnvironment.NONE)
	@ActiveProfiles("test")
	class 수집_실행_설정이_없을_때 {

		@Autowired
		private ApplicationContext context;

		@Test
		void 웹_서버_없이_컨텍스트가_시작되고_웹_보안_필터는_등록되지_않는다() {
			assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
			assertThat(context.getBeanNamesForType(CollectRunner.class)).isEmpty();
		}
	}

	@Nested
	@SpringBootTest(webEnvironment = WebEnvironment.NONE, properties = "martpick.collect.run=true")
	@ActiveProfiles("test")
	class 수집_실행_설정이_true일_때 {

		@MockitoBean
		private DataCollector dataCollector;

		@Autowired
		private ApplicationContext context;

		@Test
		void 웹_서버_없이_시작해_수집기를_한_번_실행한다() {
			assertThat(context.getBeanNamesForType(CollectRunner.class)).hasSize(1);
			then(dataCollector).should().collect();
		}
	}
}
