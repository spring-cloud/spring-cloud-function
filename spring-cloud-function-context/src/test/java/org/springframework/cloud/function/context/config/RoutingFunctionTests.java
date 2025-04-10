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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.DefaultMessageRoutingHandler;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class RoutingFunctionTests {

	private ConfigurableApplicationContext context;

	@AfterEach
	public void before() {
		System.clearProperty("spring.cloud.function.definition");
		System.clearProperty("spring.cloud.function.routing-expression");
		context.close();
	}

	private FunctionCatalog configureCatalog(Class<?> configurationClass) {
		context = new SpringApplicationBuilder(configurationClass).run(
				"--logging.level.org.springframework.cloud.function=DEBUG",
				"--spring.cloud.function.routing.enabled=true");
		return context.getBean(FunctionCatalog.class);
	}

	private FunctionCatalog configureCatalog() {
		return configureCatalog(RoutingFunctionConfiguration.class);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testDefaultRouting() {
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition", "blah").build();

		FunctionCatalog functionCatalog = this.configureCatalog(EmptyConfiguration.class);
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		try {
			function.apply(message);
			Assertions.fail();
		}
		catch (Exception e) {
			// Good
		}
		//
		functionCatalog = this.configureCatalog(ConfigurationWithDefaultMessageRoutingHandler.class);
		function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		function.apply(message);
		ConfigurationWithDefaultMessageRoutingHandler config = this.context.getBean(ConfigurationWithDefaultMessageRoutingHandler.class);
		assertThat(config.defaultHandlerInvoked).isTrue();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInvocationWithMessageAndStringHeader() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition", "reverse").build();
		assertThat(function.apply(message)).isEqualTo("olleh");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInvocationWithMessageAndListOfSingleElementHeader() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition", List.of("reverse"))
				.build();
		assertThat(function.apply(message)).isEqualTo("olleh");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testCompositionWithMessageAndListOfMultipleElementsHeader() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition",
						List.of("reverse", "uppercase"))
				.build();
		assertThat(function.apply(message)).isEqualTo("OLLEH");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInvocationWithMessageAndListOfSingleRoutingExpression() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".routing-expression",
						List.of("'reverse'"))
				.build();
		assertThat(function.apply(message)).isEqualTo("olleh");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInvocationWithMessageAndListOfMultipleRoutingExpressions() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".routing-expression",
						List.of("'uppercase'", "'reverse'"))
				.build();
		assertThat(function.apply(message)).isEqualTo("HELLO");
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
	@Test
	public void testRoutingReactiveInputWithReactiveFunctionAndDefinitionMessageHeader() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition", "echoFlux").build();
		Flux resultFlux = (Flux) function.apply(Flux.just(message));

		StepVerifier.create(resultFlux).expectError().verify();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testRoutingReactiveInputWithReactiveFunctionAndExpressionMessageHeader() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".routing-expression", "'echoFlux'").build();
		Flux resultFlux = (Flux) function.apply(Flux.just(message));
		StepVerifier.create(resultFlux).expectError().verify();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void failWithHeaderProvidedExpressionAccessingRuntime() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".routing-expression",
						"T(java.lang.Runtime).getRuntime().exec(\"open -a calculator.app\")")
				.build();
		try {
			function.apply(message);
			Assertions.fail();
		}
		catch (Exception e) {
			assertThat(e.getMessage()).isEqualTo("EL1005E: Type cannot be found 'java.lang.Runtime'");
		}

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

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInvocationWithMessageAndRoutingExpressionCaseInsensitive() {
		System.setProperty(FunctionProperties.PREFIX + ".routing-expression", "headers.function_Name");
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello").setHeader("function_name", "reverse").build();
		assertThat(function.apply(message)).isEqualTo("olleh");

		System.setProperty(FunctionProperties.PREFIX + ".routing-expression", "headers.FunCtion_namE");
		assertThat(function.apply(message)).isEqualTo("olleh");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testInvocationWithRoutingBeanExpression() {
		System.setProperty(FunctionProperties.PREFIX + ".routing-expression",
				"@reverse.apply(#root.getHeaders().get('func'))");
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello").setHeader("func", "esacreppu").build();
		assertThat(function.apply(message)).isEqualTo("HELLO");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testOtherExpectedFailures() {
		FunctionCatalog functionCatalog = this.configureCatalog();
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		// no function.definition header or function property
		try {
			function.apply(MessageBuilder.withPayload("hello").build());
			Assertions.fail();
		}
		catch (Exception e) {
			// ignore
		}

		// non existing function
		try {
			function.apply(MessageBuilder.withPayload("hello")
					.setHeader(FunctionProperties.PREFIX + ".definition", "blah").build());
			Assertions.fail();
		}
		catch (Exception e) {
			// ignore
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testInvocationWithMessageComposed() {
		FunctionCatalog functionCatalog = this.configureCatalog();

		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME + "|reverse");
		assertThat(function).isNotNull();

		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader(FunctionProperties.PREFIX + ".definition", "uppercase").build();

		assertThat(function.apply(message)).isEqualTo("OLLEH");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testMultipleRouters() {
		System.setProperty(FunctionProperties.PREFIX + ".routing-expression", "'uppercase'");
		FunctionCatalog functionCatalog = this.configureCatalog(MultipleRouterConfiguration.class);
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello").build();
		assertThat(function.apply(message)).isEqualTo("HELLO");

		function = functionCatalog.lookup("mySpecialRouter");
		assertThat(function).isNotNull();
		message = MessageBuilder.withPayload("hello").build();
		assertThat(function.apply(message)).isEqualTo("olleh");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testMultipleRoutersCaseInsensitiveKeys() {
		FunctionCatalog functionCatalog = this.configureCatalog(MultipleRouterConfiguration.class);
		Function function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME);
		assertThat(function).isNotNull();
		Message<String> message = MessageBuilder.withPayload("hello").setHeader(FunctionProperties.PREFIX + ".DeFiNition", "uppercase").build();
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
			return String::toUpperCase;
		}

		@Bean
		public Function<Flux<String>, Flux<String>> echoFlux() {
			return f -> f;
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class MultipleRouterConfiguration {

		@Bean
		RoutingFunction mySpecialRouter(FunctionCatalog functionCatalog, BeanFactory beanFactory, @Nullable MessageRoutingCallback routingCallback) {
			Map<String, String> propertiesMap = new HashMap<>();
			propertiesMap.put(FunctionProperties.PREFIX + ".routing-expression", "'reverse'");
			return new RoutingFunction(functionCatalog, propertiesMap, new BeanFactoryResolver(beanFactory), routingCallback);
		}

		@Bean
		public Function<String, String> reverse() {
			return v -> new StringBuilder(v).reverse().toString();
		}

		@Bean
		public Function<String, String> uppercase() {
			return String::toUpperCase;
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class EmptyConfiguration {
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class ConfigurationWithDefaultMessageRoutingHandler {
		public boolean defaultHandlerInvoked;
		@Bean
		public DefaultMessageRoutingHandler defaultRoutingHandler() {
			return new DefaultMessageRoutingHandler() {
				@Override
				public void accept(Message<?> message) {
					super.accept(message);
					defaultHandlerInvoked = true;
				}
			};
		}
	}
}
