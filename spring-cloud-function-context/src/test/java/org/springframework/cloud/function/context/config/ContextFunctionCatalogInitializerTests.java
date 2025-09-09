/*
 * Copyright 2012-present the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.Gson;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.scan.TestFunction;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class ContextFunctionCatalogInitializerTests {

	private GenericApplicationContext context;

	private FunctionCatalog catalog;

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void lookUps() {
		create(SimpleConfiguration.class);
		assertThat(this.context.getBean("function"))
				.isInstanceOf(FunctionRegistration.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
	}

	@Test
	public void properties() {
		create(PropertiesConfiguration.class, "app.greeting=hello");
		assertThat(this.context.getBean("function"))
				.isInstanceOf(FunctionRegistration.class);
		@SuppressWarnings("unchecked")
		Function<Flux<String>, Flux<String>> function = (Function<Flux<String>, Flux<String>>) this.catalog
				.lookup(Function.class, "function");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("hello foo");
	}

	@Test
	public void value() {
		create(ValueConfiguration.class, "app.greeting=hello");
		assertThat(this.context.getBean("function"))
				.isInstanceOf(FunctionRegistration.class);
		@SuppressWarnings("unchecked")
		Function<Flux<String>, Flux<String>> function = (Function<Flux<String>, Flux<String>>) this.catalog
				.lookup(Function.class, "function");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("hello foo");
	}

	@Test
	@Disabled
	public void compose() {
		create(SimpleConfiguration.class);
		assertThat(this.context.getBean("function"))
				.isInstanceOf(FunctionRegistration.class);
		@SuppressWarnings("unchecked")
		Supplier<Flux<String>> supplier = (Supplier<Flux<String>>) this.catalog
				.lookup(Supplier.class, "supplier|function");
		assertThat(supplier).isInstanceOf(Supplier.class);
		assertThat(supplier.get().blockFirst()).isEqualTo("HELLO");
		// TODO: support for function composition
	}

	@Test
	public void missingType() {
		try {
			create(MissingTypeConfiguration.class);
			Assertions.fail();
		}
		catch (BeanCreationException e) {
			// ignore, the test call must fail
		}
	}

	@Test
	public void dependencyInjection() {
		create(DependencyInjectionConfiguration.class);
		assertThat(this.context.getBean("foos")).isInstanceOf(FunctionRegistration.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
	}

	@Test
	public void simpleFunction() {
		create(SimpleConfiguration.class);
		Object bean = this.context.getBean("function");
		assertThat(bean).isInstanceOf(FunctionRegistration.class);
		Function<Flux<String>, Flux<Person>> function
			= this.catalog.lookup(Function.class, "function");
		assertThat(function.apply(Flux.just("{\"name\":\"foo\"}")).blockFirst().getName()).isEqualTo("FOO");
		assertThat(bean).isNotSameAs(function);
	}

	@Test
	public void scanFunction() {
		create(EmptyConfiguration.class,
				"spring.cloud.function.scan.packages=org.springframework.cloud.function.context.scan");
		Object bean = this.context.getBean(TestFunction.class.getName());
		assertThat(bean).isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> function = this.catalog
				.lookup(Function.class, TestFunction.class.getName());
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO");
		assertThat(bean).isNotSameAs(function);
	}

	@Test
	public void simpleSupplier() {
		create(SimpleConfiguration.class);
		assertThat(this.context.getBean("supplier"))
				.isInstanceOf(FunctionRegistration.class);
		Supplier<String> supplier = this.catalog.lookup(Supplier.class, "supplier");
		assertThat(supplier.get()).isEqualTo("hello");
	}

	@Test
	public void simpleConsumer() {
		create(SimpleConfiguration.class);
		assertThat(this.context.getBean("consumer"))
				.isInstanceOf(FunctionRegistration.class);
		Function<Flux<String>, Mono<Void>> consumer = this.catalog.lookup(Function.class,
				"consumer");
		consumer.apply(Flux.just("foo", "bar")).subscribe();
		assertThat(this.context.getBean(SimpleConfiguration.class).list).hasSize(2);
	}

	@Test
	public void overrideGson() {
		create(GsonConfiguration.class);
		Gson user = this.context.getBean(GsonConfiguration.class).gson();
		Gson bean = this.context.getBean(Gson.class);
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
		this.context = new GenericApplicationContext();
		Map<String, Object> map = new HashMap<>();
		for (String prop : props) {
			String[] array = StringUtils.delimitedListToStringArray(prop, "=");
			String key = array[0];
			String value = array.length > 1 ? array[1] : "";
			map.put(key, value);
		}
		if (!map.isEmpty()) {
			this.context.getEnvironment().getPropertySources()
					.addFirst(new MapPropertySource("testProperties", map));
		}
		for (ApplicationContextInitializer<GenericApplicationContext> type : types) {
			type.initialize(this.context);
		}
		new ContextFunctionCatalogInitializer.ContextFunctionCatalogBeanRegistrar(
				this.context).postProcessBeanDefinitionRegistry(this.context);
		this.context.refresh();
		this.catalog = this.context.getBean(FunctionCatalog.class);
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
			return value -> value.toUpperCase(Locale.ROOT);
		}

	}

	protected static class SimpleConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		private List<String> list = new ArrayList<>();


		@Override
		public void initialize(GenericApplicationContext context) {

			context.registerBean("function", FunctionRegistration.class,
					() -> new FunctionRegistration<>(function()).type(FunctionTypeUtils.functionType(Person.class, Person.class)));
			context.registerBean("supplier", FunctionRegistration.class,
					() -> new FunctionRegistration<>(supplier())
							.type(FunctionTypeUtils.supplierType(String.class)));
			context.registerBean("consumer", FunctionRegistration.class,
					() -> new FunctionRegistration<>(consumer())
							.type(FunctionTypeUtils.consumerType(String.class)));
			context.registerBean(SimpleConfiguration.class, () -> this);
		}

		@Bean
		public Function<Person, Person> function() {
			return person -> {
				Person p = new Person();
				p.setName(person.getName().toUpperCase(Locale.ROOT));
				return p;
			};
		}

		@Bean
		public Supplier<String> supplier() {
			return () -> "hello";
		}

		@Bean
		public Consumer<String> consumer() {
			return value -> this.list.add(value);
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
					() -> new FunctionRegistration<>(function()).type(FunctionTypeUtils.functionType(String.class, String.class)));
			context.registerBean(PropertiesConfiguration.class, () -> this);
		}

		@Bean
		public Function<String, String> function() {
			return value -> this.greeting + " " + value;
		}

	}

	protected static class ValueConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		@Value("${app.greeting}")
		private String greeting;

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("function", FunctionRegistration.class,
					() -> new FunctionRegistration<>(function()).type(FunctionTypeUtils.functionType(String.class, String.class)));
			context.registerBean(ValueConfiguration.class, () -> this);
		}

		@Bean
		public Function<String, String> function() {
			return value -> this.greeting + " " + value;
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
							.type(FunctionTypeUtils.functionType(String.class, Foo.class)));
		}

		@Bean
		public Function<String, Foo> foos(String foo) {
			return value -> new Foo(foo + ": " + value.toUpperCase(Locale.ROOT));
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
			return flux.map(foo -> new Foo(value() + ": " + foo.toUpperCase(Locale.ROOT)));
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
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

	private static class Person {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
