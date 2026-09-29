package com.martpick.publicdata;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("martpick.public-data")
public record PublicDataProperties(
		String baseUrl,
		String serviceKey,
		Duration connectTimeout,
		Duration readTimeout) {
}
