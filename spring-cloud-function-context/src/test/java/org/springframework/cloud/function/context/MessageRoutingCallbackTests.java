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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageRoutingCallbackTests {

	private ApplicationContext context;

	@BeforeEach
	public void before() {
		System.clearProperty("spring.cloud.function.definition");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testRoutingCallbackWithMessageModification() {
		FunctionCatalog catalog = this.configureCatalog(SamppleConfiguration.class);
		SamppleConfiguration conf = context.getBean(SamppleConfiguration.class);
		FunctionInvocationWrapper function = (FunctionInvocationWrapper) catalog.lookup(RoutingFunction.FUNCTION_NAME, "application/json");
		String foo = "{\"foo\":\"blah\"}";
		Message<byte[]> fooResult = (Message<byte[]>) function.apply(MessageBuilder.withPayload(foo.getBytes()).build());
		String bar = "{\"bar\":\"blah\"}";
		Message<byte[]> barResult = (Message<byte[]>) function.apply(MessageBuilder.withPayload(bar.getBytes()).build());
		assertThat(fooResult.getPayload()).isEqualTo("\"foo\"".getBytes());
		assertThat(barResult.getPayload()).isEqualTo("\"bar\"".getBytes());

		assertThat(fooResult.getHeaders().get("originalId")).isEqualTo(conf.createdMessageIds.get("foo"));
		assertThat(barResult.getHeaders().get("originalId")).isEqualTo(conf.createdMessageIds.get("bar"));
	}

	private FunctionCatalog configureCatalog(Class<?>... configClass) {
		this.context = new SpringApplicationBuilder(configClass)
				.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	@EnableAutoConfiguration
	private static class SamppleConfiguration {

		Map<String, UUID> createdMessageIds = new HashMap<>();

		@Bean
		public MessageRoutingCallback messageRoutingCallback(JsonMapper jsonMapper) {
			return new MessageRoutingCallback() {

				@Override
				public FunctionRoutingResult routingResult(Message<?> message) {
					String payload = new String((byte[]) message.getPayload());

					MessageBuilder<?> builder;
					String functionDefinition;
					if (payload.contains("foo")) {
						builder = MessageBuilder.withPayload(jsonMapper.fromJson(payload, Foo.class));
						functionDefinition = "foo";
					}
					else {
						builder = MessageBuilder.withPayload(jsonMapper.fromJson(payload, Bar.class));
						functionDefinition = "bar";
					}
					Message<?> m = builder.copyHeaders(message.getHeaders()).build();
					createdMessageIds.put(functionDefinition, m.getHeaders().getId());
					FunctionRoutingResult functionRoutingResult = new FunctionRoutingResult(functionDefinition, m);
					return functionRoutingResult;
				}
			};
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Bean
		public Function<Message<Foo>, Message<String>> foo() {
			return foo -> {
				Message m = MessageBuilder.withPayload("foo").setHeader("originalId", foo.getHeaders().getId()).build();
				createdMessageIds.put("foo", foo.getHeaders().getId());
				return m;
			};
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Bean
		public Function<Message<Bar>, Message<String>> bar() {
			return bar -> {
				Message m = MessageBuilder.withPayload("bar").setHeader("originalId", bar.getHeaders().getId()).build();
				createdMessageIds.put("bar", bar.getHeaders().getId());
				return m;
			};
		}
	}


	public static class Foo {
		private String foo;

		public String getFoo() {
			return foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
		}
	}

	public static class Bar {
		private String bar;

		public String getBar() {
			return bar;
		}

		public void setBar(String bar) {
			this.bar = bar;
		}
	}
}
