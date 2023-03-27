package com.example.demo;

import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.context.DefaultMessageRoutingHandler;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

@SpringBootApplication
public class NativeUppercaseApplication {

	Log logger = LogFactory.getLog(NativeUppercaseApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(NativeUppercaseApplication.class, args);
	}

	@Bean
	public MessageRoutingCallback customRouter() {
		return new MessageRoutingCallback() {
			@Override
			public String routingResult(Message<?> message) {
				logger.info("Received message: " + message);
				return (String) message.getHeaders().get("spring.cloud.function.definition");
			}
		};
	}

	@Bean
	public Function<String, String> uppercase() {
		return v -> {
			System.out.println("Uppercasing " + v);
			return v.toUpperCase();
		};
	}

	@Bean
	public Function<String, String> lowercase() {
		return v -> {
			System.out.println("Lowercasing " + v);
			return v.toUpperCase();
		};
	}

}
