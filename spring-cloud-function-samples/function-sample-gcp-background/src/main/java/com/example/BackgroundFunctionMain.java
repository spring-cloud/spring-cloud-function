package com.example;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
		return message -> {
			// The PubSubMessage data field arrives as a base-64 encoded string and must be decoded.
			// See: https://cloud.google.com/functions/docs/calling/pubsub#event_structure
			String decodedMessage = new String(Base64.getDecoder().decode(message.getData()), StandardCharsets.UTF_8);
			System.out.println("Received Pub/Sub message with data: " + decodedMessage);
		};
	}
}
