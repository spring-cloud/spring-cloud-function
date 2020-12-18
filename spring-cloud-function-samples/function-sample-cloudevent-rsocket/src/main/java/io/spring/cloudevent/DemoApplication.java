package io.spring.cloudevent;

import java.util.function.Consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.cloudevent.CloudEventMessageBuilder;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) throws Exception {
	    SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public Consumer<Person> hire(StreamBridge streamBridge) {
		return person -> {
			Employee employee = new Employee(person);
			streamBridge.send("hire-out-0", CloudEventMessageBuilder.withData(employee)
				.setSource("http://spring.io/rsocket")
				.setId("1234567890")
				.build());
		};
	}
}
