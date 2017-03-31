/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.cloud.function.context;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class ContextFunctionCatalogAutoConfigurationTests {

	private ConfigurableApplicationContext context;
	private InMemoryFunctionCatalog catalog;

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void simpleFunction() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
	}

	@Test
	public void simpleSupplier() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("supplier")).isInstanceOf(Supplier.class);
		Supplier<Flux<String>> supplier = catalog.lookupSupplier("supplier");
		assertThat(supplier.get().blockFirst()).isEqualTo("hello");
	}

	@Test
	public void simpleConsumer() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("consumer")).isInstanceOf(Consumer.class);
		Consumer<Flux<String>> consumer = catalog.lookupConsumer("consumer");
		consumer.accept(Flux.just("foo", "bar"));
		assertThat(context.getBean(SimpleConfiguration.class).list).hasSize(2);
	}

	@Test
	public void qualifiedBean() {
		create(QualifiedConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isNull();
		assertThat(catalog.lookupFunction("other")).isInstanceOf(Function.class);
	}

	@Test
	public void aliasBean() {
		create(AliasConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isNotNull();
		assertThat(catalog.lookupFunction("other")).isInstanceOf(Function.class);
	}

	@Test
	public void registrationBean() {
		create(RegistrationConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isNull();
		assertThat(catalog.lookupFunction("registration")).isNull();
		assertThat(catalog.lookupFunction("other")).isInstanceOf(Function.class);
	}

	private void create(Class<?>... types) {
		context = new SpringApplicationBuilder((Object[]) types).run();
		catalog = context.getBean(InMemoryFunctionCatalog.class);
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SimpleConfiguration {
		private List<String> list = new ArrayList<>();
		@Bean
		public Function<String, String> function() {
			return value -> value.toUpperCase();
		}
		@Bean
		public Supplier<String> supplier() {
			return () -> "hello";
		}
		@Bean
		public Consumer<String> consumer() {
			return value -> list.add(value);
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class QualifiedConfiguration {
		@Bean
		@Qualifier("other")
		public Function<String, String> function() {
			return value -> value.toUpperCase();
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class AliasConfiguration {
		@Bean({"function", "other"})
		public Function<String, String> function() {
			return value -> value.toUpperCase();
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class RegistrationConfiguration {
		@Bean
		public FunctionRegistration<Function<String, String>> registration() {
			return new FunctionRegistration<Function<String, String>>(function()).name("other");
		}
		@Bean
		public Function<String, String> function() {
			return value -> value.toUpperCase();
		}
	}

}
