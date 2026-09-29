package com.martpick.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CollectRunnerTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(DataCollector.class, () -> mock(DataCollector.class))
			.withUserConfiguration(CollectRunner.class);

	@Test
	void 수집_실행_설정이_없으면_수집기를_실행하지_않는다() {
		contextRunner.run(context -> assertThat(context).doesNotHaveBean(CollectRunner.class));
	}

	@Test
	void 수집_실행_설정이_false이면_수집기를_실행하지_않는다() {
		contextRunner.withPropertyValues("martpick.collect.run=false")
				.run(context -> assertThat(context).doesNotHaveBean(CollectRunner.class));
	}

	@Test
	void 수집_실행_설정이_true이면_수집_실행기를_등록한다() {
		contextRunner.withPropertyValues("martpick.collect.run=true")
				.run(context -> assertThat(context).hasSingleBean(CollectRunner.class));
	}
}
