package com.example.demo;

import java.util.Locale;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.adapter.aws.AWSLambdaUtils;
import org.springframework.context.annotation.Bean;
// import org.springframework.cloud.function.context.DefaultMessageRoutingHandler;
// import org.springframework.cloud.function.context.MessageRoutingCallback;
// import org.springframework.messaging.Message;
import org.springframework.messaging.Message;

import com.amazonaws.services.lambda.runtime.Context;

@SpringBootApplication
public class NativeFunctionApplication {

	Log logger = LogFactory.getLog(NativeFunctionApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(NativeFunctionApplication.class, args);
	}

//	@Bean
//	public MessageRoutingCallback customRouter() {
//		return new MessageRoutingCallback() {
//			@Override
//			public String routingResult(Message<?> message) {
//				logger.info("Received message: " + message);
//				return (String) message.getHeaders().get("spring.cloud.function.definition");
//			}
//		};
//	}

	@Bean
	public Function<Message<String>, String> uppercase() {
		return message -> {
			System.out.println("AWS Context: " + message.getHeaders().get(AWSLambdaUtils.AWS_CONTEXT));
			System.out.println("Uppercasing " + message.getPayload());
			return message.getPayload().toUpperCase(Locale.ROOT);
		};
	}

	@Bean
	public Function<String, String> reverse() {
		return v -> {
			System.out.println("Reversing " + v);
			return new StringBuilder(v).reverse().toString();
		};
	}

}
