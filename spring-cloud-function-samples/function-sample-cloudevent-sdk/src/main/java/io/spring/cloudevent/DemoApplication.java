package io.spring.cloudevent;

import java.net.URI;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.spring.messaging.CloudEventMessageConverter;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) throws Exception {
	    SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public Function<CloudEvent, CloudEvent> echo() {
		return ce -> {
			System.out.println("Received: " + ce);
			return CloudEventBuilder.from(ce)
					.withId(UUID.randomUUID().toString())
					.withSource(URI.create("https://spring.io/foos"))
					.withType("io.spring.event.Foo")
					.withData(ce.getData().toBytes())
					.build();
		};
	}
	
	@Bean
	public CloudEventMessageConverter cloudEventMessageConverter() {
		return new CloudEventMessageConverter();
	}
}
