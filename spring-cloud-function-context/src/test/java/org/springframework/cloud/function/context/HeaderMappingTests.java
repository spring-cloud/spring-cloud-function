/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.context;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

//NOTE!!! assertions for input in all tests are in 'echo' function since we're validating what's coming into it.
public class HeaderMappingTests {

	@Test
	public void testErrorWarnAndContinue() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.echoFail.input-header-mapping-expression[0].key1=hello1",
						"--spring.cloud.function.configuration.echoFail.input-header-mapping-expression[0].key2='hello2'",
						"--spring.cloud.function.configuration.echoFail.input-header-mapping-expression[0].foo=headers.contentType")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("echoFail");
			function.apply(MessageBuilder.withPayload("helo").build());
		}
	}

	@Test
	public void testInputHeaderMappingPropertyWithIndex() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression[0].key1='hello1'",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression[0].key2='hello2'",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression[0].foo=headers.contentType")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("echo");
			function.apply(MessageBuilder.withPayload("helo")
					.setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build());
		}
	}

	@Test
	public void testInputHeaderMappingPropertyWithIndexMix() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression[0].key1='hello1'",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression[0].key2='hello2'",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression.foo=headers.contentType")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("echo");
			function.apply(MessageBuilder.withPayload("helo")
					.setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build());
		}
	}

	@Test
	public void testInputHeaderMappingPropertyWithIndexMixDeux() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression.key1='hello1'",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression[0].key2='hello2'",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression.foo=headers.contentType")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("echo");
			function.apply(MessageBuilder.withPayload("helo")
					.setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build());
		}
	}

	@Test
	public void testInputHeaderMappingPropertyWithoutIndex() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression.key1='hello1'",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression.key2='hello2'",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression.foo=headers.contentType")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("echo");
			function.apply(MessageBuilder.withPayload("helo")
					.setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build());
		}
	}

	@Test
	public void testInputHeaderMappingExpressionWithCompositionWithIndex() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression[0].key1='hello1'",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression[0].key2='hello2'",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression[0].foo=headers.contentType")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("echo|foo");
			function.apply(MessageBuilder.withPayload("helo")
					.setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build());
		}
	}

	@Test
	public void testInputHeaderMappingExpressionWithCompositionWithoutIndex() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression.key1='hello1'",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression.key2='hello2'",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression.foo=headers.contentType")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("echo|foo");
			function.apply(MessageBuilder.withPayload("helo")
					.setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build());

			//assertions are in 'echo' function since we're validating what's coming into it.
		}
	}

	@Test
	public void testInputHeaderMappingPropertyWithSplitExpression() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.split.input-header-mapping-expression.key1=headers.path.split('/')[0]",
						"--spring.cloud.function.configuration.split.input-header-mapping-expression.key2=headers.path.split('/')[1]",
						"--spring.cloud.function.configuration.split.input-header-mapping-expression.key3=headers.path")) {
			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("split");
			function.apply(MessageBuilder.withPayload("helo")
					.setHeader(MessageHeaders.CONTENT_TYPE, "application/json")
					.setHeader("path", "foo/bar/baz").build());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOutputHeaderMapping() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.foo.output-header-mapping-expression.keyOut1='hello1'",
						"--spring.cloud.function.configuration.foo.output-header-mapping-expression.keyOut2=headers.contentType")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("foo");
			Message<byte[]> result = (Message<byte[]>) function.apply(MessageBuilder.withPayload("helo")
					.setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build());
			assertThat(result.getHeaders().containsKey("keyOut1")).isTrue();
			assertThat(result.getHeaders().get("keyOut1")).isEqualTo("hello1");
			assertThat(result.getHeaders().containsKey("keyOut2")).isTrue();
			assertThat(result.getHeaders().get("keyOut2")).isEqualTo("application/json");
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testMixedInputOutputHeaderMapping() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.configuration.split.output-header-mapping-expression.keyOut1='hello1'",
						"--spring.cloud.function.configuration.split.output-header-mapping-expression.keyOut2=headers.contentType",
						"--spring.cloud.function.configuration.split.input-header-mapping-expression.key1=headers.path.split('/')[0]",
						"--spring.cloud.function.configuration.split.input-header-mapping-expression.key2=headers.path.split('/')[1]",
						"--spring.cloud.function.configuration.split.input-header-mapping-expression.key3=headers.path")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = functionCatalog.lookup("split");
			Message<byte[]> result = (Message<byte[]>) function.apply(MessageBuilder.withPayload("helo")
					.setHeader(MessageHeaders.CONTENT_TYPE, "application/json")
					.setHeader("path", "foo/bar/baz").build());
			assertThat(result.getHeaders().containsKey("keyOut1")).isTrue();
			assertThat(result.getHeaders().get("keyOut1")).isEqualTo("hello1");
			assertThat(result.getHeaders().containsKey("keyOut2")).isTrue();
			assertThat(result.getHeaders().get("keyOut2")).isEqualTo("application/json");
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SampleFunctionConfiguration {

		@Bean
		public Function<Message<?>, Message<?>> echo() {
			return m -> {
				assertThat(m.getHeaders().get("key1")).isEqualTo("hello1");
				assertThat(m.getHeaders().get("key2")).isEqualTo("hello2");
				assertThat(m.getHeaders().get("foo")).isEqualTo("application/json");
				return m;
			};
		}

		@Bean
		public Function<Message<?>, Message<?>> echoFail() {
			return m -> {
				assertThat(m.getHeaders().containsKey("key1")).isFalse();
				assertThat(m.getHeaders().get("key2")).isEqualTo("hello2");
				assertThat(m.getHeaders().containsKey("foo")).isFalse();
				return m;
			};
		}

		@Bean
		public Function<Message<?>, Message<?>> split() {
			return m -> {
				assertThat(m.getHeaders().get("key1")).isEqualTo("foo");
				assertThat(m.getHeaders().get("key2")).isEqualTo("bar");
				assertThat(m.getHeaders().get("key3")).isEqualTo("foo/bar/baz");
				return m;
			};
		}

		@Bean
		public Function<Message<?>, Message<?>> foo() {
			return x -> {
				assertThat(x.getHeaders().containsKey("keyOut1")).isFalse();
				return x;
			};
		}
	}
}
