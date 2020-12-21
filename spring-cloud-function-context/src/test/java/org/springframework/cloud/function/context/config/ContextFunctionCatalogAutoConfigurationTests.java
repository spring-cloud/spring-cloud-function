/*
 * Copyright 2012-2020 the original author or authors.
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

import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.inject.FooConfiguration;
import org.springframework.cloud.function.scan.ScannedFunction;
import org.springframework.cloud.function.test.GenericFunction;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Artem Bilan
 * @author Oleg Zhurakousky
 * @author Anshul Mehra
 */
public class ContextFunctionCatalogAutoConfigurationTests {

	private ConfigurableApplicationContext context;

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
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(this.context.getBean("function2")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class,
				"function,function2")).isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> f = this.catalog.lookup(Function.class,
				"function,function2,function3");
		assertThat(f).isInstanceOf(Function.class);
		assertThat(f.apply(Flux.just("hello")).blockFirst())
				.isEqualTo("HELLOfunction2function3");
		assertThat(this.context.getBean("supplierFoo")).isInstanceOf(Supplier.class);
		assertThat((Supplier<?>) this.catalog.lookup(Supplier.class, "supplierFoo"))
				.isInstanceOf(Supplier.class);
		assertThat(this.context.getBean("supplier_Foo")).isInstanceOf(Supplier.class);
	}

	@Test
	// do we really need this test and behavior? What does this even mean?
	public void ambiguousFunction() {
		create(AmbiguousConfiguration.class);
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat((Supplier<?>) this.catalog.lookup(Supplier.class, "foos"))
				.isInstanceOf(Supplier.class);
		Class<?> inputType = ((FunctionInvocationWrapper) this.catalog.lookup(Function.class, "foos")).getRawInputType();
		assertThat(inputType).isEqualTo(String.class);
		FunctionInvocationWrapper function = this.catalog.lookup("foos");
		Type outputType = function.getOutputType();
		assertThat((Class<?>) FunctionTypeUtils.getGenericType(outputType)).isEqualTo(Foo.class);
	}

	@Test
	public void configurationFunction() {
		create(FunctionConfiguration.class);
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup(Function.class, "foos");
		assertThat(function).isInstanceOf(Function.class);
		Type inputType = function.getInputType();
		assertThat(FunctionTypeUtils.getGenericType(inputType)).isEqualTo(String.class);
		Type outputType = function.getOutputType();
		assertThat((Class<?>) FunctionTypeUtils.getGenericType(outputType)).isEqualTo(Foo.class);
	}

	@Test
	public void dependencyInjection() {
		create(DependencyInjectionConfiguration.class);
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		Class<?> inputType = ((FunctionInvocationWrapper) this.catalog.lookup(Function.class, "foos")).getRawInputType();
		assertThat(inputType).isEqualTo(String.class);
	}

	@Test
	public void externalDependencyInjection() {
		create(ExternalDependencyConfiguration.class);
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		Class<?> inputType = ((FunctionInvocationWrapper) this.catalog.lookup(Function.class, "foos")).getRawInputType();
		assertThat(inputType).isEqualTo(String.class);
	}

	@Test
	public void composedFunction() {
		create(MultipleConfiguration.class);
		FunctionInvocationWrapper function = this.catalog.lookup(Function.class, "foos");
		assertThat(function).isInstanceOf(Function.class);

		function = this.catalog.lookup(Function.class, "foos,bars");
		Class<?> inputType = function.getRawInputType();
		assertThat(inputType).isAssignableFrom(String.class);
		Class<?> outputType = function.getRawOutputType();
		assertThat(outputType).isAssignableFrom(Bar.class);
	}

	@Test
	public void composedSupplier() {
		create(MultipleConfiguration.class);
		FunctionInvocationWrapper function = this.catalog.lookup("names,foos");
		assertThat(function).isInstanceOf(Supplier.class);
		assertThat(function.getRawOutputType()).isAssignableFrom(Foo.class);
		assertThat(function.getRawInputType()).isNull();
	}

	@Test
	public void composedConsumer() {
		create(MultipleConfiguration.class);
		FunctionInvocationWrapper function = this.catalog.lookup("foos,print");
		assertThat(function).isInstanceOf(Consumer.class);
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.getRawInputType()).isAssignableFrom(String.class);
		assertThat(function.getRawOutputType()).isNull();
	}

	@Test
	public void genericFunction() {
		create(GenericConfiguration.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.getRawInputType()).isAssignableFrom(Map.class);
	}

	@Test
	public void fluxMessageFunction() {
		create(FluxMessageConfiguration.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.isInputTypeMessage()).isTrue();

		final Type inputType = function.getInputType();

		assertThat(FunctionTypeUtils.getRawType(inputType)).isAssignableFrom(Flux.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(inputType))).isAssignableFrom(Message.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(FunctionTypeUtils.getGenericType(inputType)))).isAssignableFrom(String.class);
	}

	@Test
	public void publisherMessageFunction() {
		create(PublisherMessageConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.isInputTypeMessage()).isTrue();

		final Type inputType = function.getInputType();

		assertThat(FunctionTypeUtils.getRawType(inputType)).isAssignableFrom(Publisher.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(inputType))).isAssignableFrom(Message.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(FunctionTypeUtils.getGenericType(inputType)))).isAssignableFrom(String.class);

	}

	@Test
	public void monoFunction() {
		create(MonoConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.isInputTypeMessage()).isFalse();
		Type inputType = function.getInputType();
		Type outputType = function.getOutputType();
		assertThat((Class<?>) FunctionTypeUtils.getGenericType(inputType)).isAssignableFrom(String.class);
		assertThat(FunctionTypeUtils.getRawType(inputType)).isAssignableFrom(Flux.class);
		assertThat(FunctionTypeUtils.getRawType(outputType)).isAssignableFrom(Mono.class);
	}

	@Test
	public void monoToMonoNonVoidFunction() {
		create(MonoToMonoNonVoidConfiguration.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		Type inputType = function.getInputType();
		assertThat((Class<?>) FunctionTypeUtils.getGenericType(inputType)).isAssignableFrom(String.class);
		Type outputType = function.getOutputType();
		assertThat((Class<?>) FunctionTypeUtils.getGenericType(outputType)).isAssignableFrom(String.class);
	}

	@Test
	public void messageFunction() {
		create(MessageConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.isInputTypeMessage()).isTrue();
		assertThat(function.isOutputTypeMessage()).isTrue();
		Type inputType = function.getInputType();
		assertThat((Class<?>) FunctionTypeUtils.getGenericType(inputType)).isAssignableFrom(String.class);
	}

	@Test
	public void genericFluxFunction() {
		create(GenericFluxConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		Type inputType = function.getInputType();
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(inputType))).isAssignableFrom(Map.class);
		assertThat(FunctionTypeUtils.getRawType(inputType)).isAssignableFrom(Flux.class);
	}

	@Test
	public void externalFunction() {
		create(ExternalConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		Type inputType = function.getInputType();
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(inputType))).isAssignableFrom(Map.class);
	}

	@Test
	public void singletonFunction() {
		create(SingletonConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.isInputTypePublisher()).isFalse();
		assertThat(function.isOutputTypePublisher()).isFalse();
		Type inputType = function.getInputType();
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(inputType))).isAssignableFrom(Integer.class);
	}

	@Test
	@Disabled
	public void singletonMessageFunction() {
		create(SingletonMessageConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(function.isInputTypeMessage()).isTrue();
		Type inputType = function.getInputType();
		assertThat((Class<?>) FunctionTypeUtils.getGenericType(inputType)).isAssignableFrom(Integer.class);
	}

	@Test
	public void nonParametericTypeFunction() {
		create(NonParametricTypeSingletonConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		Type inputType = function.getInputType();
		assertThat((Class<?>) FunctionTypeUtils.getGenericType(inputType)).isAssignableFrom(Integer.class);
	}

	@Test
	public void componentScanBeanFunction() {
		create(ComponentScanBeanConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		Type inputType = function.getInputType();
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(inputType))).isAssignableFrom(Map.class);
	}

	@Test
	public void componentScanFunction() {
		create(ComponentScanConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		FunctionInvocationWrapper function = this.catalog.lookup("function");
		assertThat(function).isInstanceOf(Function.class);
		Type inputType = function.getInputType();
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(inputType))).isAssignableFrom(Map.class);
	}

	@Test
	public void componentScanJarFunction() {
		try {
			create("greeter.jar", ComponentScanJarConfiguration.class);
			assertThat(this.context.getBean("greeter")).isInstanceOf(Function.class);
			FunctionInvocationWrapper function = this.catalog.lookup("greeter");
			assertThat(function).isInstanceOf(Function.class);
			Type inputType = function.getInputType();
			assertThat((Class<?>) FunctionTypeUtils.getGenericType(inputType)).isAssignableFrom(String.class);
		}
		finally {
			ClassUtils.overrideThreadContextClassLoader(getClass().getClassLoader());
		}
	}

	private void create(String jarfile, Class<?> config, String... props) {
		try {
			URL[] urls = new URL[] { new ClassPathResource(jarfile).getURL() };
			ClassUtils.overrideThreadContextClassLoader(
					new URLClassLoader(urls, getClass().getClassLoader()));
			create(config, props);
		}
		catch (Exception e) {
			ReflectionUtils.rethrowRuntimeException(e);
		}
	}

	@Test
	public void simpleFunction() {
		create(SimpleConfiguration.class);
		Object bean = this.context.getBean("function");
		assertThat(bean).isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> function = this.catalog
				.lookup(Function.class, "function");
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO");
		assertThat(bean).isNotSameAs(function);
	}

	@Test
	@Disabled
	public void simpleSupplier() {
		create(SimpleConfiguration.class);
		assertThat(this.context.getBean("supplier")).isInstanceOf(Supplier.class);
		Supplier<Flux<String>> supplier = this.catalog.lookup(Supplier.class, "supplier");
		assertThat(supplier.get().blockFirst()).isEqualTo("hello");
	}

	@Test
	public void simpleConsumer() {
		create(SimpleConfiguration.class);
		assertThat(this.context.getBean("consumer")).isInstanceOf(Consumer.class);
		Function<Flux<String>, Mono<Void>> consumer = this.catalog.lookup(Function.class,
				"consumer");
		consumer.apply(Flux.just("foo", "bar")).subscribe();
		assertThat(this.context.getBean(SimpleConfiguration.class).list).hasSize(2);
	}

	@Test
	@Disabled
	public void qualifiedBean() {
		create(QualifiedConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup("function")).isNull();
		assertThat((Function<?, ?>) this.catalog.lookup("other")).isNotNull();
		assertThat(FunctionTypeUtils.getGenericType(((FunctionInvocationWrapper) this.catalog.lookup("other")).getInputType()))
						.isEqualTo(String.class);
	}

	@Test
	public void aliasBean() {
		create(AliasConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isNotNull();
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "other"))
				.isInstanceOf(Function.class);
	}

	@Test
	@Disabled
	public void registrationBean() {
		create(RegistrationConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "registration")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "other"))
				.isInstanceOf(Function.class);
	}

	@Test
	public void factoryBeanFunction() {
		create(FactoryBeanConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> f = this.catalog.lookup(Function.class,
				"function");
		assertThat(f.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO-bar");
	}

	@Test
	public void functionCatalogDependentBeanFactoryPostProcessor() {
		create(new Class[]{ComponentFunctionConfiguration.class, AppendFunction.class});
		assertThat(this.context.getBean("appendFunction")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "appendFunction"))
			.isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> f = this.catalog.lookup(Function.class,
			"appendFunction");
		assertThat(f.apply(Flux.just("World")).blockFirst()).isEqualTo("Hello World");
	}

	private void create(Class<?> type, String... props) {
		create(new Class<?>[] { type }, props);
	}

	private void create(Class<?>[] types, String... props) {
		this.context = new SpringApplicationBuilder(types).properties(props).run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
//		this.inspector = this.context.getBean(FunctionInspector.class);
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class EmptyConfiguration {

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
		public Function<String, String> function2() {
			return value -> value + "function2";
		}

		@Bean
		public Function<String, String> function3() {
			return value -> value + "function3";
		}

		@Bean
		public Supplier<String> supplier() {
			return () -> "hello";
		}

		@Bean(name = { "supplierFoo", "supplier_Foo" })
		public Supplier<String> foo() {
			return () -> "hello";
		}

		@Bean
		public Consumer<String> consumer() {
			return value -> {
				this.list.add(value);
			};
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class ComponentFunctionConfiguration {
		@Bean
		public String value() {
			return "Hello ";
		}

		@Bean
		public BeanFactoryPostProcessor someBeanFactoryPostProcessor(Environment environment,
			@Nullable FunctionRegistry functionCatalog) {
			return beanFactory -> { };
		}
	}

	@Component("appendFunction")
	public static class AppendFunction implements Function<String, String> {
		private String value;

		public AppendFunction(String value) {
			this.value = value;
		}

		@Override
		public String apply(String s) {
			return this.value + s;
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class DependencyInjectionConfiguration {

		@Bean
		public Function<String, Foo> foos(String foo) {
			return value -> new Foo(foo + ": " + value.toUpperCase());
		}

		@Bean
		public String value() {
			return "Hello";
		}

	}

	@EnableAutoConfiguration
	@Configuration("foos")
	protected static class FunctionConfiguration
			implements Function<Flux<String>, Flux<Foo>> {

		@Override
		public Flux<Foo> apply(Flux<String> flux) {
			return flux.map(foo -> new Foo(value() + ": " + foo.toUpperCase()));
		}

		@Bean
		public String value() {
			return "Hello";
		}

	}

	@EnableAutoConfiguration
	@Configuration
	@ComponentScan(basePackageClasses = FooConfiguration.class)
	protected static class ExternalDependencyConfiguration {

		@Bean
		public String value() {
			return "Hello";
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class AmbiguousConfiguration {

		@Bean
		public Function<String, Foo> foos() {
			return value -> new Foo(value.toUpperCase());
		}

		@Bean
		@Qualifier("foos")
		public Supplier<Foo> supplier() {
			return () -> new Foo("bar");
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class MultipleConfiguration {

		@Bean
		public Function<String, Foo> foos() {
			return value -> new Foo(value.toUpperCase());
		}

		@Bean
		public Function<Foo, Bar> bars() {
			return value -> new Bar(value.getValue());
		}

		@Bean
		public Consumer<Foo> print() {
			return System.out::println;
		}

		@Bean
		public Supplier<String> names() {
			return () -> "Mark";
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class GenericConfiguration {

		@Bean
		public Function<Map<String, String>, Map<String, String>> function() {
			return m -> m.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
					e -> e.getValue().toString().toUpperCase()));
		}

	}

	@EnableAutoConfiguration
	@Configuration
	@Import(GenericFunction.class)
	protected static class ExternalConfiguration {

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SingletonConfiguration implements BeanFactoryPostProcessor {

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
				throws BeansException {
			beanFactory.registerSingleton("function", new SingletonFunction());
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SingletonMessageConfiguration
			implements BeanFactoryPostProcessor {

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
				throws BeansException {
			beanFactory.registerSingleton("function", new SingletonMessageFunction());
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class NonParametricTypeSingletonConfiguration {

		@Bean
		public SingletonFunction function() {
			return new SingletonFunction();
		}

	}

	protected static class SingletonFunction implements Function<Integer, String> {

		@Override
		public String apply(Integer input) {
			return "value=" + input;
		}

	}

	protected static class SingletonMessageFunction
			implements Function<Message<Integer>, Message<String>> {

		@Override
		public Message<String> apply(Message<Integer> input) {
			return MessageBuilder.withPayload("value=" + input.getPayload()).build();
		}

	}

	@EnableAutoConfiguration
	@Configuration
	@ComponentScan(basePackageClasses = GenericFunction.class)
	protected static class ComponentScanBeanConfiguration {

	}

	@EnableAutoConfiguration
	@Configuration
	@ComponentScan(basePackageClasses = ScannedFunction.class)
	protected static class ComponentScanConfiguration {

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class ComponentScanJarConfiguration {

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class GenericFluxConfiguration {

		@Bean
		public Function<Flux<Map<String, String>>, Flux<Map<String, String>>> function() {
			return flux -> flux.map(m -> m.entrySet().stream().collect(Collectors
					.toMap(e -> e.getKey(), e -> e.getValue().toString().toUpperCase())));
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class FluxMessageConfiguration {

		@Bean
		public Function<Flux<Message<String>>, Flux<Message<String>>> function() {
			return flux -> flux.map(m -> MessageBuilder
					.withPayload(m.getPayload().toUpperCase()).build());
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class PublisherMessageConfiguration {

		@Bean
		public Function<Publisher<Message<String>>, Publisher<Message<String>>> function() {
			return flux -> Flux.from(flux).map(m -> MessageBuilder
					.withPayload(m.getPayload().toUpperCase()).build());
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class MonoConfiguration {

		@Bean
		public Function<Flux<String>, Mono<Map<String, Integer>>> function() {
			return flux -> flux.collect(HashMap::new,
					(map, word) -> map.merge(word, 1, Integer::sum));
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class MonoToMonoNonVoidConfiguration {

		@Bean
		public Function<Mono<String>, Mono<String>> function() {
			return mono -> mono;
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class MessageConfiguration {

		@Bean
		public Function<Message<String>, Message<String>> function() {
			return m -> MessageBuilder.withPayload(m.getPayload().toUpperCase()).build();
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

		@Bean({ "function", "other" })
		public Function<String, String> function() {
			return value -> value.toUpperCase();
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class RegistrationConfiguration {

		@Bean
		public FunctionRegistration<Function<String, String>> registration() {
			return new FunctionRegistration<Function<String, String>>(function(),
					"other");
		}

		@Bean
		public Function<String, String> function() {
			return value -> value.toUpperCase();
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class FactoryBeanConfiguration
			implements BeanDefinitionRegistryPostProcessor {

		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
				throws BeansException {
			RootBeanDefinition beanDefinition = new RootBeanDefinition(
					FunctionFactoryBean.class);
			beanDefinition.setSource(new DescriptiveResource("Function"));
			registry.registerBeanDefinition("function", beanDefinition);
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
				throws BeansException {

		}

	}

	private static class FunctionFactoryBean
			extends AbstractFactoryBean<Function<String, String>> {

		@Override
		public Class<?> getObjectType() {
			return Function.class;
		}

		@Override
		protected Function<String, String> createInstance() throws Exception {
			return s -> s.toUpperCase() + "-bar";
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

	public static class Bar {

		private String message;

		public Bar(String value) {
			this.message = value;
		}

		Bar() {
		}

		public String getMessage() {
			return this.message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

}
