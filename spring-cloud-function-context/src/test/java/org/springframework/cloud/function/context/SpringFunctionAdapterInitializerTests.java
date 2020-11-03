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

package org.springframework.cloud.function.context;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class SpringFunctionAdapterInitializerTests {

	private AbstractSpringFunctionAdapterInitializer<Object> initializer;

	@AfterEach
	public void after() {
		System.clearProperty("function.name");
		if (this.initializer != null) {
			this.initializer.close();
		}
	}

	@Test
	public void nullSource() {
		Assertions.assertThrows(IllegalArgumentException.class, () ->
			this.initializer = new AbstractSpringFunctionAdapterInitializer<Object>(null) {

			});
	}

	@Test
	public void sourceAsMainClassProperty() {
		try {
			System.setProperty("MAIN_CLASS", FluxFunctionConfig.class.getName());
			this.initializer = new AbstractSpringFunctionAdapterInitializer<Object>() {

			};
		}
		finally {
			System.clearProperty("MAIN_CLASS");
		}
	}

	@Test
	public void functionBean() {
		this.initializer = new AbstractSpringFunctionAdapterInitializer<Object>(FluxFunctionConfig.class) {

		};
		this.initializer.initialize(null);
		Flux<?> result = Flux.from(this.initializer.apply(Flux.just(new Foo())));
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Test
	public void functionApp() {
		this.initializer = new AbstractSpringFunctionAdapterInitializer<Object>(FluxFunctionApp.class) {

		};
		this.initializer.initialize(null);
		Flux<?> result = Flux.from(this.initializer.apply(Flux.just(new Foo())));
		Object o = result.blockFirst();
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Test
	public void functionCatalog() {
		this.initializer = new AbstractSpringFunctionAdapterInitializer<Object>(FunctionConfig.class) {

		};
		this.initializer.initialize(null);
		Flux<?> result = Flux.from(this.initializer.apply(Flux.just(new Foo())));
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Test
	@Disabled // related to boot 2.1 no bean override change
	public void functionRegistrar() {
		this.initializer = new AbstractSpringFunctionAdapterInitializer<Object>(FunctionRegistrar.class) {

		};
		this.initializer.initialize(null);
		Flux<?> result = Flux.from(this.initializer.apply(Flux.just(new Foo())));
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Test
	public void namedFunctionCatalog() {
		this.initializer = new AbstractSpringFunctionAdapterInitializer<Object>(NamedFunctionConfig.class) {

		};
		System.setProperty("function.name", "other");
		this.initializer.initialize(null);
		Flux<?> result = Flux.from(this.initializer.apply(Flux.just(new Foo())));
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Test
	public void consumerCatalog() {
		this.initializer = new AbstractSpringFunctionAdapterInitializer<Object>(ConsumerConfig.class) {

		};
		this.initializer.initialize(null);
		Flux<?> result = Flux.from(this.initializer.apply(Flux.just(new Foo())));
		assertThat(result.toStream().collect(Collectors.toList())).isEmpty();
	}

	@Test
	public void supplierCatalog() {
		initializer = new AbstractSpringFunctionAdapterInitializer<Object>(SupplierConfig.class) {

		};
		initializer.initialize(null);
		Flux result = Flux.from(initializer.apply(null));
		assertThat(result.blockFirst()).isInstanceOf(Bar.class);
	}

	@Configuration
	protected static class FluxFunctionConfig {

		@Bean
		public Function<Flux<Foo>, Flux<Bar>> function() {
			return flux -> flux.map(foo -> new Bar());
		}

	}

	protected static class FluxFunctionApp implements Function<Flux<Foo>, Flux<Bar>> {

		@Override
		public Flux<Bar> apply(Flux<Foo> flux) {
			return flux.map(foo -> new Bar());
		}

	}

	protected static class FunctionRegistrar
			implements ApplicationContextInitializer<GenericApplicationContext> {

		public Function<Flux<Foo>, Flux<Bar>> function() {
			return flux -> flux.map(foo -> new Bar());
		}

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("function", FunctionRegistration.class,
					() -> new FunctionRegistration<Function<Flux<Foo>, Flux<Bar>>>(
							function()).name("function")
									.type(FunctionType.from(Foo.class).to(Bar.class)
											.wrap(Flux.class).getType()));
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
			return () -> {
				return new Bar();
			};
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
