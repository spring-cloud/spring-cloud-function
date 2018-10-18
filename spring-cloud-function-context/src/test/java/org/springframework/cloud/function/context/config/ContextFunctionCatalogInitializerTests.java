/*
 * Copyright 2017-2018 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.Gson;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 *
 */
public class ContextFunctionCatalogInitializerTests {

	private GenericApplicationContext context;
	private FunctionCatalog catalog;
	private FunctionInspector inspector;

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void lookUps() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(FunctionRegistration.class);
		assertThat((Function<?, ?>) catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
	}

	@Test
	public void properties() {
		create(PropertiesConfiguration.class, "app.greeting=hello");
		assertThat(context.getBean("function")).isInstanceOf(FunctionRegistration.class);
		@SuppressWarnings("unchecked")
		Function<Flux<String>, Flux<String>> function = (Function<Flux<String>, Flux<String>>) catalog
				.lookup(Function.class, "function");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("hello foo");
	}

	@Test
	public void value() {
		create(ValueConfiguration.class, "app.greeting=hello");
		assertThat(context.getBean("function")).isInstanceOf(FunctionRegistration.class);
		@SuppressWarnings("unchecked")
		Function<Flux<String>, Flux<String>> function = (Function<Flux<String>, Flux<String>>) catalog
				.lookup(Function.class, "function");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("hello foo");
	}

	@Test
	@Ignore
	public void compose() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(FunctionRegistration.class);
		@SuppressWarnings("unchecked")
		Supplier<Flux<String>> supplier = (Supplier<Flux<String>>) catalog
				.lookup(Supplier.class, "supplier|function");
		assertThat(supplier).isInstanceOf(Supplier.class);
		assertThat(supplier.get().blockFirst()).isEqualTo("HELLO");
		// TODO: support for function composition
	}

	@Test(expected = BeanCreationException.class)
	public void missingType() {
		create(MissingTypeConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(FunctionRegistration.class);
		assertThat((Function<?, ?>) catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		// TODO: support for type inference from functional bean registrations
	}

	@Test
	public void configurationFunction() {
		create(FunctionConfiguration.class);
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "foos")))
				.isEqualTo(String.class);
		assertThat(inspector.getOutputType(catalog.lookup(Function.class, "foos")))
				.isEqualTo(Foo.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "foos")))
				.isEqualTo(Flux.class);
	}

	@Test
	public void dependencyInjection() {
		create(DependencyInjectionConfiguration.class);
		assertThat(context.getBean("foos")).isInstanceOf(FunctionRegistration.class);
		assertThat((Function<?, ?>) catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "foos")))
				.isEqualTo(String.class);
	}

	@Test
	public void simpleFunction() {
		create(SimpleConfiguration.class);
		Object bean = context.getBean("function");
		assertThat(bean).isInstanceOf(FunctionRegistration.class);
		Function<Flux<String>, Flux<String>> function = catalog.lookup(Function.class,
				"function");
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO");
		assertThat(bean).isNotSameAs(function);
		assertThat(inspector.getRegistration(function)).isNotNull();
		assertThat(inspector.getRegistration(function).getType()).isEqualTo(
				FunctionType.from(String.class).to(String.class).wrap(Flux.class));
	}

	@Test
	public void simpleSupplier() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("supplier")).isInstanceOf(FunctionRegistration.class);
		Supplier<Flux<String>> supplier = catalog.lookup(Supplier.class, "supplier");
		assertThat(supplier.get().blockFirst()).isEqualTo("hello");
	}

	@Test
	public void simpleConsumer() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("consumer")).isInstanceOf(FunctionRegistration.class);
		Function<Flux<String>, Mono<Void>> consumer = catalog.lookup(Function.class,
				"consumer");
		consumer.apply(Flux.just("foo", "bar")).subscribe();
		assertThat(context.getBean(SimpleConfiguration.class).list).hasSize(2);
	}

	@Test
	public void overrideGson() {
		create(GsonConfiguration.class);
		Gson user = context.getBean(GsonConfiguration.class).gson();
		Gson bean = context.getBean(Gson.class);
		assertThat(user).isSameAs(bean);
	}

	@SuppressWarnings("unchecked")
	private void create(
			Class<? extends ApplicationContextInitializer<GenericApplicationContext>> type,
			String... props) {
		create(Arrays.asList(BeanUtils.instantiateClass(type))
				.toArray(new ApplicationContextInitializer[0]), props);
	}

	private void create(ApplicationContextInitializer<GenericApplicationContext>[] types,
			String... props) {
		context = new GenericApplicationContext();
		Map<String, Object> map = new HashMap<>();
		for (String prop : props) {
			String[] array = StringUtils.delimitedListToStringArray(prop, "=");
			String key = array[0];
			String value = array.length > 1 ? array[1] : "";
			map.put(key, value);
		}
		if (!map.isEmpty()) {
			context.getEnvironment().getPropertySources()
					.addFirst(new MapPropertySource("testProperties", map));
		}
		for (ApplicationContextInitializer<GenericApplicationContext> type : types) {
			type.initialize(context);
		}
		new ContextFunctionCatalogInitializer.ContextFunctionCatalogBeanRegistrar(context)
				.postProcessBeanDefinitionRegistry(context);
		context.refresh();
		catalog = context.getBean(FunctionCatalog.class);
		inspector = context.getBean(FunctionInspector.class);
	}

	protected static class EmptyConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		@Override
		public void initialize(GenericApplicationContext applicationContext) {
		}
	}

	protected static class MissingTypeConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("function", FunctionRegistration.class,
					() -> new FunctionRegistration<>(function()));
		}

		@Bean
		public Function<String, String> function() {
			return value -> value.toUpperCase();
		}

	}

	protected static class SimpleConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		private List<String> list = new ArrayList<>();

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("function", FunctionRegistration.class,
					() -> new FunctionRegistration<>(function()).type(
							FunctionType.from(String.class).to(String.class).getType()));
			context.registerBean("supplier", FunctionRegistration.class,
					() -> new FunctionRegistration<>(supplier())
							.type(FunctionType.supplier(String.class).getType()));
			context.registerBean("consumer", FunctionRegistration.class,
					() -> new FunctionRegistration<>(consumer())
							.type(FunctionType.consumer(String.class).getType()));
			context.registerBean(SimpleConfiguration.class, () -> this);
		}

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

	@ConfigurationProperties("app")
	protected static class PropertiesConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		private String greeting;

		public String getGreeting() {
			return this.greeting;
		}

		public void setGreeting(String greeting) {
			this.greeting = greeting;
		}

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("function", FunctionRegistration.class,
					() -> new FunctionRegistration<>(function()).type(
							FunctionType.from(String.class).to(String.class).getType()));
			context.registerBean(PropertiesConfiguration.class, () -> this);
		}

		@Bean
		public Function<String, String> function() {
			return value -> greeting + " " + value;
		}

	}

	protected static class ValueConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		@Value("${app.greeting}")
		private String greeting;

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("function", FunctionRegistration.class,
					() -> new FunctionRegistration<>(function()).type(
							FunctionType.from(String.class).to(String.class).getType()));
			context.registerBean(ValueConfiguration.class, () -> this);
		}

		@Bean
		public Function<String, String> function() {
			return value -> greeting + " " + value;
		}

	}

	protected static class GsonConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		private Gson gson = new Gson();

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("gson", Gson.class, this::gson);
			context.registerBean(GsonConfiguration.class, () -> this);
		}

		@Bean
		public Gson gson() {
			return this.gson;
		}

	}

	protected static class DependencyInjectionConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean(String.class, () -> value());
			context.registerBean("foos", FunctionRegistration.class,
					() -> new FunctionRegistration<>(foos(context.getBean(String.class)))
							.type(FunctionType.from(String.class).to(Foo.class)
									.getType()));
		}

		@Bean
		public Function<String, Foo> foos(String foo) {
			return value -> new Foo(foo + ": " + value.toUpperCase());
		}

		@Bean
		public String value() {
			return "Hello";
		}
	}

	protected static class FunctionConfiguration
			implements Function<Flux<String>, Flux<Foo>>,
			ApplicationContextInitializer<GenericApplicationContext> {

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("foos", FunctionConfiguration.class, () -> this);
			context.registerBean("function", FunctionRegistration.class,
					() -> new FunctionRegistration<>(this, "foos")
							.type(FunctionConfiguration.class));
		}

		@Override
		public Flux<Foo> apply(Flux<String> flux) {
			return flux.map(foo -> new Foo(value() + ": " + foo.toUpperCase()));
		}

		@Bean
		public String value() {
			return "Hello";
		}
	}

	public static class Foo {
		private String value;

		public Foo(String value) {
			this.value = value;
		}

		Foo() {
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

}
