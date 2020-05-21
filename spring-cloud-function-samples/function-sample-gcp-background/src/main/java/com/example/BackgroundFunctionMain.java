package com.example;

import java.util.function.Consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BackgroundFunctionMain {

	public static void main(String[] args) {
		SpringApplication.run(BackgroundFunctionMain.class, args);
	}

	/**
	 * The background function which triggers on an event from Pub/Sub and consumes the Pub/Sub
	 * event message.
	 */
	@Bean
	public Consumer<PubSubMessage> pubSubFunction() {
		return message -> System.out.println("Received Pub/Sub message with data: " + message.getData());
	}
}
