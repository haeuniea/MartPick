package com.martpick.collect;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * martpick.collect.run=true일 때만 기동 시 1회 수집한다. 실패하면 예외로 기동이 실패해 0이 아닌 코드로 종료된다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "martpick.collect.run", havingValue = "true")
public class CollectRunner implements ApplicationRunner {

	private final DataCollector dataCollector;

	@Override
	public void run(ApplicationArguments args) {
		dataCollector.collect();
	}
}
