/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;

import org.springframework.cloud.function.context.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class SpringFunctionInitializerTests {
	
	private SpringFunctionInitializer initializer;

	@After
	public void after() {
		System.clearProperty("function.name");
		if (initializer!=null) {
			initializer.close();
		}
	}

	@Test
	public void functionBean() {
		initializer = new SpringFunctionInitializer(
				FluxFunctionConfig.class);
		initializer.initialize();
		Flux<?> result = initializer.apply(Flux.just(new Foo()));
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Test
	public void functionCatalog() {
		initializer = new SpringFunctionInitializer(
				FunctionConfig.class);
		initializer.initialize();
		Flux<?> result = initializer.apply(Flux.just(new Foo()));
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Test
	public void namedFunctionCatalog() {
		initializer = new SpringFunctionInitializer(
				NamedFunctionConfig.class);
		System.setProperty("function.name", "other");
		initializer.initialize();
		Flux<?> result = initializer.apply(Flux.just(new Foo()));
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Test
	public void consumerCatalog() {
		initializer = new SpringFunctionInitializer(
				ConsumerConfig.class);
		initializer.initialize();
		Flux<?> result = initializer.apply(Flux.just(new Foo()));
		assertThat(result.toStream().collect(Collectors.toList())).isEmpty();
	}

	@Test
	public void supplierCatalog() {
		initializer = new SpringFunctionInitializer(
				SupplierConfig.class);
		initializer.initialize();
		Flux<?> result = initializer.apply(Flux.empty());
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Configuration
	protected static class FluxFunctionConfig {
		@Bean
		public Function<Flux<Foo>, Flux<Bar>> function() {
			return flux -> flux.map(foo -> new Bar());
		}
	}

	@Configuration
	@Import(ContextFunctionCatalogAutoConfiguration.class)
	protected static class FunctionConfig {
		@Bean
		public Function<Foo, Bar> function() {
			return foo -> new Bar();
		}
	}

	@Configuration
	@Import(ContextFunctionCatalogAutoConfiguration.class)
	protected static class NamedFunctionConfig {
		@Bean
		public Function<Foo, Bar> other() {
			return foo -> new Bar();
		}
	}

	@Configuration
	@Import(ContextFunctionCatalogAutoConfiguration.class)
	protected static class SupplierConfig {
		@Bean
		public Supplier<Bar> supplier() {
			return () -> new Bar();
		}
	}

	@Configuration
	@Import(ContextFunctionCatalogAutoConfiguration.class)
	protected static class ConsumerConfig {
		@Bean
		public Consumer<Foo> consumer() {
			return foo -> {
			};
		}
	}

	protected static class Foo {
	}

	protected static class Bar {
	}
}
