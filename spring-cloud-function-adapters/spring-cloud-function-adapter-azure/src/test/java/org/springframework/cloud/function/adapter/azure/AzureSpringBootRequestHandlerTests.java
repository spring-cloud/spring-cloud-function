/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class AzureSpringBootRequestHandlerTests {

	private AzureSpringBootRequestHandler<?, ?> handler = null;

	<I, O> AzureSpringBootRequestHandler<I, O> handler(Class<?> config) {
		AzureSpringBootRequestHandler<I, O> handler = new AzureSpringBootRequestHandler<I, O>(
				config);
		this.handler = handler;
		return handler;
	}

	@Test
	public void bareConfig() {
		AzureSpringBootRequestHandler<Foo, Bar> handler = handler(BareConfig.class);
		Bar bar = handler.handleRequest(new Foo("bar"),
				new TestExecutionContext("uppercase"));
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@Test
	public void autoConfig() {
		AzureSpringBootRequestHandler<Foo, Bar> handler = handler(AutoConfig.class);
		Bar bar = handler.handleRequest(new Foo("bar"),
				new TestExecutionContext("uppercase"));
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@Test
	public void multiConfig() {
		AzureSpringBootRequestHandler<Foo, Bar> handler = handler(MultiConfig.class);
		Bar bar = handler.handleRequest(new Foo("bar"),
				new TestExecutionContext("uppercase"));
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@Test
	public void implicitListConfig() {
		AzureSpringBootRequestHandler<List<Foo>, List<Bar>> handler = handler(
				AutoConfig.class);
		List<Bar> bar = handler.handleRequest(Arrays.asList(new Foo("bar")),
				new TestExecutionContext("uppercase"));
		assertThat(bar).hasSize(1);
		assertThat(bar.get(0).getValue()).isEqualTo("BAR");
	}

	@Test
	public void listToListConfig() {
		AzureSpringBootRequestHandler<List<Foo>, List<Bar>> handler = handler(
				ListConfig.class);
		List<Bar> bar = handler.handleRequest(
				Arrays.asList(new Foo("bar"), new Foo("baz")),
				new TestExecutionContext("uppercase"));
		assertThat(bar).hasSize(2);
		assertThat(bar.get(0).getValue()).isEqualTo("BAR");
	}

	@Test
	public void listToListSingleConfig() {
		AzureSpringBootRequestHandler<List<Foo>, List<Bar>> handler = handler(
				ListConfig.class);
		List<Bar> bar = handler.handleRequest(Arrays.asList(new Foo("bar")),
				new TestExecutionContext("uppercase"));
		assertThat(bar).hasSize(1);
		assertThat(bar.get(0).getValue()).isEqualTo("BAR");
	}

	@Test
	public void collectConfig() {
		AzureSpringBootRequestHandler<List<Foo>, Bar> handler = handler(
				CollectConfig.class);
		Bar bar = handler.handleRequest(Arrays.asList(new Foo("bar")),
				new TestExecutionContext("uppercase"));
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@After
	public void close() throws IOException {
		if (handler != null)
			handler.close();
	}

	@Configuration
	protected static class BareConfig {
		@Bean
		public Function<Flux<Foo>, Flux<Bar>> function() {
			return foos -> foos.map(foo -> new Bar(foo.getValue().toUpperCase()));
		}
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class AutoConfig {
		@Bean
		public Function<Foo, Bar> uppercase() {
			return foo -> new Bar(foo.getValue().toUpperCase());
		}
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class ListConfig {
		@Bean
		public Function<List<Foo>, List<Bar>> uppercase() {
			return foos -> foos.stream().map(foo -> new Bar(foo.getValue().toUpperCase()))
					.collect(Collectors.toList());
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
		public Function<Foo, Bar> uppercase() {
			return foo -> new Bar(foo.getValue().toUpperCase());
		}

		@Bean
		public Function<Bar, Foo> lowercase() {
			return bar -> new Foo(bar.getValue().toLowerCase());
		}
	}

}

class Foo {

	private String value;

	Foo() {
	}

	public String lowercase() {
		return value.toLowerCase();
	}

	public Foo(String value) {
		this.value = value;
	}

	public String uppercase() {
		return value.toUpperCase();
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}

class Bar {

	private String value;

	Bar() {
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