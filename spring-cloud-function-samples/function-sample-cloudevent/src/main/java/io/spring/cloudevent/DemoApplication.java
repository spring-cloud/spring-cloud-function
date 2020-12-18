package io.spring.cloudevent;

import java.net.URI;
import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.cloudevent.CloudEventHeaderEnricher;
import org.springframework.cloud.function.cloudevent.CloudEventMessageBuilder;
import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) throws Exception {
	    SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public Function<Person, Employee> hire() {
		return person -> {
			Employee employee = new Employee(person);
			return employee;
		};
	}

	// uncomment while keeping the above POJO function
//	@Bean
//	public CloudEventHeaderEnricher cloudEventEnricher() {
//		return messageBuilder -> messageBuilder.setSource("http://spring.io/cloudevent")
//				.setType("sample").setId("987654");
//	}

	// uncomment while commenting the previous two beans
//	@Bean
//	public Function<Message<Person>, Message<Employee>> hire() {
//		return message -> {
//			Person person = message.getPayload();
//			Employee employee = new Employee(person);
//			return CloudEventMessageBuilder.withData(employee).setId("123456")
//				.setSource(URI.create("https://spring.cloudevenets.sample")).build();
//		};
//	}
}
