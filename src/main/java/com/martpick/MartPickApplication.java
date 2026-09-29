package com.martpick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.martpick.config.MartPickProperties;

@SpringBootApplication
@EnableConfigurationProperties(MartPickProperties.class)
public class MartPickApplication {

	public static void main(String[] args) {
		SpringApplication.run(MartPickApplication.class, args);
	}

}
