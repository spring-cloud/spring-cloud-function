/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.adapter.azure;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class FunctionInvokerTests {

	private FunctionInvoker<?, ?> handler = null;

	<I, O> FunctionInvoker<I, O> handler(Class<?> config) {
		FunctionInvoker<I, O> handler = new FunctionInvoker<I, O>(
				config);
		this.handler = handler;
		return handler;
	}

//	@Test // this is wrong too since function is Flux, Flux and while input may be single value, the output can still be multiple
	public void bareConfig() {
		FunctionInvoker<Foo, Bar> handler = handler(BareConfig.class);
		Bar bar = handler.handleRequest(new Foo("bar"),
				new TestExecutionContext("uppercase"));
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@Test
	public void autoConfig() {
		FunctionInvoker<Foo, Bar> handler = handler(AutoConfig.class);
		Bar bar = handler.handleRequest(new Foo("bar"),
				new TestExecutionContext("uppercase"));
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@Test
	public void multiConfig() {
		FunctionInvoker<Foo, Bar> handler = handler(MultiConfig.class);
		Bar bar = handler.handleRequest(new Foo("bar"),
				new TestExecutionContext("uppercase"));
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@Test
	public void implicitListConfig() {
		FunctionInvoker<List<Foo>, List<Bar>> handler = handler(
				AutoConfig.class);
		List<Bar> bar = handler.handleRequest(Arrays.asList(new Foo("bar"), new Foo("baz")),
				new TestExecutionContext("uppercase"));
		assertThat(bar).hasSize(2);
		assertThat(bar.get(0).getValue()).isEqualTo("BAR");
		assertThat(bar.get(1).getValue()).isEqualTo("BAZ");
	}

	@Test
	public void listToListConfig() {
		FunctionInvoker<List<Foo>, List<Bar>> handler = handler(
				ListConfig.class);
		List<Bar> bar = handler.handleRequest(
				Arrays.asList(new Foo("bar"), new Foo("baz")),
				new TestExecutionContext("uppercase"));
		assertThat(bar).hasSize(2);
		assertThat(bar.get(0).getValue()).isEqualTo("BAR");
	}

	@Test
	public void listToListSingleConfig() {
		FunctionInvoker<List<Foo>, List<Bar>> handler = handler(
				ListConfig.class);
		List<Bar> bar = handler.handleRequest(Arrays.asList(new Foo("bar")),
				new TestExecutionContext("uppercase"));
		assertThat(bar).hasSize(1);
		assertThat(bar.get(0).getValue()).isEqualTo("BAR");
	}

	@Test
	public void collectConfig() {
		FunctionInvoker<List<Foo>, Bar> handler = handler(
				CollectConfig.class);
		Bar bar = handler.handleRequest(Arrays.asList(new Foo("bar")),
				new TestExecutionContext("uppercase"));
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@Test
	public void functionNonFluxBean() {
		FunctionInvoker<Foo, Bar> handler = handler(NonFluxFunctionConfig.class);
		Bar bar = handler.handleRequest(new Foo("bar"), new TestExecutionContext("function"));
		assertThat(bar).isNotNull();
	}

	@Test
	public void supplierNonFluxBean() {
		FunctionInvoker<Void, List<String>> handler = handler(NonFluxSupplierConfig.class);
		List<String> result = handler.handleRequest(new TestExecutionContext("supplier"));

		assertThat(result).isNotEmpty();
		assertThat(result.toString()).isEqualTo("[foo1, foo2]");
	}

	@Test
	public void supplierPublisherBean() {
		FunctionInvoker<Void, ?> handler = handler(ReactiveSupplierConfig.class);
		Foo resultSingle = (Foo) handler.handleRequest(new TestExecutionContext("suppliermono"));
		assertThat(resultSingle.getValue()).isEqualTo("hello");

		List<Foo> resultList = (List<Foo>) handler.handleRequest(new TestExecutionContext("supplierflux"));
		assertThat(resultList.size()).isEqualTo(2);
	}

	private static String consumerResult;

	@Test
	public void consumerNonFluxBean() {
		FunctionInvoker<String, Void> handler = handler(NonFluxConsumerConfig.class);
		Object result = handler.handleRequest("foo1", new TestExecutionContext("consumer"));

		assertThat(result).isNull();
		assertThat(consumerResult).isEqualTo("foo1");
	}

	@Test
	public void testReactiveFunctions() {
		FunctionInvoker<String, String> handler = handler(ReactiveFunctionConfiguration.class);
		String result = handler.handleRequest("hello", new TestExecutionContext("uppercaseMono"));

		System.out.println(result);

//		assertThat(result).isNull();
//		assertThat(consumerResult).isEqualTo("foo1");
	}

	@AfterEach
	public void close() throws IOException {
		if (this.handler != null) {
			this.handler.close();
		}
	}

	@Configuration
	protected static class NonFluxFunctionConfig {

		@Bean
		public Function<Foo, Bar> function() {
			return foo -> new Bar();
		}

	}

	@Configuration
	@EnableAutoConfiguration
	protected static class ReactiveFunctionConfiguration {

		@Bean
		public Function<Flux<String>, Flux<String>> echoStream() {
			return f -> f;
		}

		@Bean
		public Function<Mono<String>, Mono<String>> uppercaseMono() {
			return f -> f.map(v -> v.toUpperCase());
		}

	}

	@Configuration
	protected static class NonFluxSupplierConfig {

		@Bean
		public Supplier<List<String>> supplier() {
			return () -> Arrays.asList("foo1", "foo2");
		}

	}

	@Configuration
	protected static class NonFluxConsumerConfig {

		@Bean
		public Consumer<String> consumer() {
			return (v) -> consumerResult = v;
		}

	}

	@Configuration
//	@EnableAutoConfiguration
	protected static class ReactiveSupplierConfig {

		@Bean
		public Supplier<Mono<Foo>> suppliermono() {
			return () -> Mono.just(new Foo("hello"));
		}

		@Bean
		public Supplier<Flux<Foo>> supplierflux() {
			return () -> Flux.just(new Foo("hello"), new Foo("bye"));
		}

	}

	@Configuration
	protected static class BareConfig {

		@Bean("uppercase")
		public Function<Flux<Foo>, Flux<Bar>> function() {
			return foos -> foos.map(foo -> new Bar(foo.getValue().toUpperCase()));
		}

	}

	@Configuration
	@EnableAutoConfiguration
	protected static class AutoConfig {

		@Bean
		public Function<Message<Foo>, Bar> uppercase() {
			return message -> {
				Foo foo = message.getPayload();
				ExecutionContext targetContext = (ExecutionContext) message.getHeaders().get("executionContext");
				targetContext.getLogger().info("Invoking 'uppercase' on " + foo.getValue());
				return new Bar(foo.getValue().toUpperCase());
			};
		}

	}

	@Configuration
	@EnableAutoConfiguration
	protected static class ListConfig {

		@Bean
		public Function<List<Foo>, List<Bar>> uppercase() {
			return foos -> {
				List<Bar> bars = foos.stream().map(foo -> new Bar(foo.getValue().toUpperCase()))
						.collect(Collectors.toList());
				return bars;
			};
		}

	}

	@Configuration
	@EnableAutoConfiguration
	protected static class CollectConfig {

		@Bean
		public Function<List<Foo>, Bar> uppercase() {
			return foos -> new Bar(foos.stream().map(foo -> foo.getValue().toUpperCase())
					.collect(Collectors.joining(",")));
		}

	}

	@Configuration
	@EnableAutoConfiguration
	protected static class MultiConfig {

		@Bean
		public Function<Message<Foo>, Bar> uppercase() {

			return message -> {
				ExecutionContext context = (ExecutionContext) message.getHeaders().get("executionContext");
				Foo foo = message.getPayload();
				context.getLogger().info("Executing uppercase function");
				return new Bar(foo.getValue().toUpperCase());
			};
		}

		@Bean
		public Function<Message<Bar>, Foo> lowercase() {
			return message -> {
				ExecutionContext context = (ExecutionContext) message.getHeaders().get("executionContext");
				Bar bar = message.getPayload();
				context.getLogger().info("Executing lowercase function");
				return new Foo(bar.getValue().toLowerCase());
			};
		}

	}

}

class Foo {

	private String value;

	Foo() {
	}

	Foo(String value) {
		this.value = value;
	}

	public String lowercase() {
		return this.value.toLowerCase();
	}

	public String uppercase() {
		return this.value.toUpperCase();
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

}

class Bar {

	private String value;

	Bar() {
	}

	Bar(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

}
