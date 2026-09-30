package com.martpick.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/**
 * 운영 설정 파일(application.yml)의 수집 재시도 값이 PROJECT_SPEC 4-1-1과 같은지 확인한다.
 */
class MartPickPropertiesBindingTest {

	@Test
	void 재시도는_호출당_2회_대기_1초_2초_수집당_총_30회로_설정되어_있다() throws IOException {
		MartPickProperties properties = bindApplicationYml();

		assertThat(properties.collect().retryDelays()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
		assertThat(properties.collect().maxTotalRetries()).isEqualTo(30);
	}

	@Test
	void 수집은_명시적으로_켜지_않으면_실행되지_않는다() throws IOException {
		assertThat(bindApplicationYml().collect().run()).isFalse();
	}

	@Test
	void 기준_상품은_초기_7개_상품이다() throws IOException {
		assertThat(bindApplicationYml().collect().baselineGoodIds())
				.containsExactly(1000L, 1003L, 1008L, 292L, 254L, 224L, 993L);
	}

	private static MartPickProperties bindApplicationYml() throws IOException {
		Binder binder = new Binder(ConfigurationPropertySources.from(
				new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))));
		return binder.bind("martpick", MartPickProperties.class).get();
	}
}
