package org.springframework.cloud.function.adapter.aws;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringBootApiGatewayRequestHandlerTests {

	private SpringBootApiGatewayRequestHandler handler;

	@Test
	public void functionBean() {
		handler = new SpringBootApiGatewayRequestHandler(FunctionConfig.class);
		handler.initialize();

		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
		request.setBody("{\"value\":\"foo\"}");

		Object output = handler.handleRequest(request, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode()).isEqualTo(200);
		assertThat(((APIGatewayProxyResponseEvent) output).getBody()).isEqualTo("{\"value\":\"FOO\"}");
	}

	@Configuration
	@Import({ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class})
	protected static class FunctionConfig {
		@Bean
		public Function<Foo, Bar> function() {
			return foo -> new Bar(foo.getValue().toUpperCase());
		}
	}

	@Test
	public void functionMessageBean() {
		handler = new SpringBootApiGatewayRequestHandler(FunctionMessageConfig.class);
		handler.initialize();

		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
		request.setBody("{\"value\":\"foo\"}");

		Object output = handler.handleRequest(request, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode()).isEqualTo(200);
		assertThat(((APIGatewayProxyResponseEvent) output).getHeaders().get("spring")).isEqualTo("cloud");
		assertThat(((APIGatewayProxyResponseEvent) output).getBody()).isEqualTo("{\"value\":\"FOO\"}");
	}

	@Configuration
	@Import({ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class})
	protected static class FunctionMessageConfig {
		@Bean
		public Function<Message<Foo>, Message<Bar>> function() {
			return (foo -> {
				Map<String, Object> headers = Collections.singletonMap("spring", "cloud");
				return new GenericMessage<>(
						new Bar(foo.getPayload().getValue().toUpperCase()),
						headers);
			});
		}
	}

	protected static class Foo {
		private String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	protected static class Bar {
		private String value;

		public Bar() {
		}

		public Bar(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}
}
