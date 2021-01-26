package com.example;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.util.ObjectUtils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

@SpringBootApplication
public class LambdaApplication {

	private static Log logger = LogFactory.getLog(LambdaApplication.class);

	@Bean
	public Consumer<String> consume() {
		return value -> {
			logger.info("Consuming: " + value);
		};
	}

	@Bean
	public Function<String, String> uppercase() {
		return value -> {
			logger.info("UPPERCASING: " + value);
			return value.toUpperCase();
		};
	}

	@Bean
	public Function<APIGatewayProxyRequestEvent, String> extractPayloadFromGatewayEvent() {
		return value -> {
			logger.info("ECHO Payload from Gateway Event: " + value.getBody());
			return value.getBody();
		};
	}

	@Bean
	public Function<Message<String>, Message<String>> echoMessage() {
		return value -> {
			logger.info("ECHO MESSAGE: " + value);
			return value;
		};
	}

	@Bean
	public Function<String, String> reverse() {
		return value -> {
			logger.info("REVERSING: " + value);
			return new StringBuilder(value).reverse().toString();
		};
	}



	public static void main(String[] args) {
		logger.info("==> Starting: LambdaApplication");
		if (!ObjectUtils.isEmpty(args)) {
			logger.info("==>  args: " + Arrays.asList(args));
		}
		SpringApplication.run(LambdaApplication.class, args);
	}

}
