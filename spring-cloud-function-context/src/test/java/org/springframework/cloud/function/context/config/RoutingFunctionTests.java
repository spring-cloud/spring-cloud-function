/*
 * Copyright 2019-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context.config;

import java.util.function.Function;

import org.junit.After;
import org.junit.Test;
import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class RoutingFunctionTests {

	private ConfigurableApplicationContext context;

	@After
	public void before() {
		System.clearProperty("spring.cloud.function.definition");
		System.clearProperty("spring.cloud.function.routing-expression");
		context.close();
	}

	private FunctionCatalog configureCatalog() {
		context = new SpringApplicationBuilder(RoutingFunctionConfiguration.class).run(
				"--logging.level.org.springframework.cloud.function=DEBUG",
				"--spring.cloud.function.routing.enabled=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInvocationWithMessageAndHeader() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition", "reverse").build();
		assertThat(function.apply(message)).isEqualTo("olleh");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testRoutingSimpleInputWithReactiveFunctionWithMessageHeader() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition", "echoFlux").build();
		assertThat(((Flux) function.apply(message)).blockFirst()).isEqualTo("hello");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test(expected = Exception.class)
	public void testRoutingReactiveInputWithReactiveFunctionAndDefinitionMessageHeader() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition", "echoFlux").build();
		Flux resultFlux = (Flux) function.apply(Flux.just(message));
		resultFlux.subscribe();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test(expected = Exception.class)
	public void testRoutingReactiveInputWithReactiveFunctionAndExpressionMessageHeader() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".routing-expression", "'echoFlux'").build();
		Flux resultFlux = (Flux) function.apply(Flux.just(message));
		resultFlux.subscribe();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInvocationWithMessageAndDefinitionProperty() {
		System.setProperty(FunctionProperties.PREFIX + ".definition", "reverse");
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello").build();
		assertThat(function.apply(message)).isEqualTo("olleh");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInvocationWithMessageAndRoutingExpression() {
		System.setProperty(FunctionProperties.PREFIX + ".routing-expression", "headers.function_name");
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello").setHeader("function_name", "reverse").build();
		assertThat(function.apply(message)).isEqualTo("olleh");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testOtherExpectedFailures() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		// no function.definition header or function property
		try {
			function.apply(MessageBuilder.withPayload("hello").build());
			fail();
		}
		catch (Exception e) {
			//ignore
		}

		// non existing function
		try {
			function.apply(MessageBuilder.withPayload("hello").setHeader(FunctionProperties.PREFIX + ".definition", "blah").build());
			fail();
		}
		catch (Exception e) {
			//ignore
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testInvocationWithMessageComposed() {
		FunctionCatalog functionCatalog = this.configureCatalog();

		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME + "|uppercase");
		assertThat(function).isNotNull();

		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition", "uppercase").build();

		assertThat(function.apply(message)).isEqualTo("HELLO");
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class RoutingFunctionConfiguration {

		@Bean
		public Function<String, String> reverse() {
			return v -> new StringBuilder(v).reverse().toString();
		}

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		@Bean
		public Function<Flux<String>, Flux<String>> echoFlux() {
			return f -> f;
		}
	}
}
