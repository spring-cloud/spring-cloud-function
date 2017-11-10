/*
 * Copyright 2017 the original author or authors.
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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.core.FunctionCatalog;
import org.springframework.cloud.function.scan.ScannedFunction;
import org.springframework.cloud.function.test.GenericFunction;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class ContextFunctionCatalogAutoConfigurationTests {

	private ConfigurableApplicationContext context;
	private FunctionCatalog catalog;
	private FunctionInspector inspector;
	private static String value;

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
		ContextFunctionCatalogAutoConfigurationTests.value = null;
	}

	@Test
	public void lookUps() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
		assertThat(context.getBean("function2")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function,function2"))
				.isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> f = catalog
				.lookupFunction("function,function2,function3");
		assertThat(f).isInstanceOf(Function.class);
		assertThat(f.apply(Flux.just("hello")).blockFirst())
				.isEqualTo("HELLOfunction2function3");
		assertThat(context.getBean("supplierFoo")).isInstanceOf(Supplier.class);
		assertThat(catalog.lookupSupplier("supplierFoo")).isInstanceOf(Supplier.class);
		assertThat(context.getBean("supplier_Foo")).isInstanceOf(Supplier.class);
		assertThat(catalog.lookupSupplier("supplier_Foo")).isInstanceOf(Supplier.class);
	}

	@Test
	public void ambiguousFunction() {
		create(AmbiguousConfiguration.class);
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("foos")).isInstanceOf(Function.class);
		assertThat(catalog.lookupConsumer("foos")).isInstanceOf(Consumer.class);
		assertThat(inspector.getInputType(catalog.lookupFunction("foos")))
				.isEqualTo(String.class);
		assertThat(inspector.getInputType(catalog.lookupConsumer("foos")))
				.isEqualTo(Foo.class);

	}

	@Test
	public void composedFunction() {
		create(MultipleConfiguration.class);
		assertThat(catalog.lookupFunction("foos,bars")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("names,foos")).isNull();
		assertThat(inspector.getInputType(catalog.lookupFunction("foos,bars")))
				.isAssignableFrom(String.class);
		assertThat(inspector.getOutputType(catalog.lookupFunction("foos,bars")))
				.isAssignableFrom(Bar.class);
	}

	@Test
	public void composedSupplier() {
		create(MultipleConfiguration.class);
		assertThat(catalog.lookupSupplier("names,foos")).isInstanceOf(Supplier.class);
		assertThat(catalog.lookupFunction("names,foos")).isNull();
		assertThat(inspector.getOutputType(catalog.lookupSupplier("names,foos")))
				.isAssignableFrom(Foo.class);
		// The input type is the same as the output type of the first element in the chain
		assertThat(inspector.getInputType(catalog.lookupSupplier("names,foos")))
				.isAssignableFrom(String.class);
	}

	@Test
	public void composedConsumer() {
		create(MultipleConfiguration.class);
		assertThat(catalog.lookupConsumer("foos,print")).isInstanceOf(Consumer.class);
		assertThat(catalog.lookupFunction("foos,print")).isNull();
		assertThat(inspector.getInputType(catalog.lookupConsumer("foos,print")))
				.isAssignableFrom(String.class);
		// The output type is the same as the input type of the last element in the chain
		assertThat(inspector.getOutputType(catalog.lookupConsumer("foos,print")))
				.isAssignableFrom(Foo.class);
	}

	@Test
	public void genericFunction() {
		create(GenericConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookupFunction("function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("function")))
				.isAssignableFrom(Map.class);
	}

	@Test
	public void fluxMessageFunction() {
		create(FluxMessageConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
		assertThat(inspector.isMessage(catalog.lookupFunction("function"))).isTrue();
		assertThat(inspector.getInputType(catalog.lookupFunction("function")))
				.isAssignableFrom(String.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("function")))
				.isAssignableFrom(Flux.class);
	}

	@Test
	public void messageFunction() {
		create(MessageConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
		assertThat(inspector.isMessage(catalog.lookupFunction("function"))).isTrue();
		assertThat(inspector.getInputType(catalog.lookupFunction("function")))
				.isAssignableFrom(String.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("function")))
				.isAssignableFrom(String.class);
	}

	@Test
	public void genericFluxFunction() {
		create(GenericFluxConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookupFunction("function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("function")))
				.isAssignableFrom(Flux.class);
	}

	@Test
	public void externalFunction() {
		create(ExternalConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookupFunction("function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("function")))
				.isAssignableFrom(Map.class);
	}

	@Test
	public void singletonFunction() {
		create(SingletonConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookupFunction("function")))
				.isAssignableFrom(Integer.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("function")))
				.isAssignableFrom(Integer.class);
	}

	@Test
	public void componentScanBeanFunction() {
		create(ComponentScanBeanConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookupFunction("function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("function")))
				.isAssignableFrom(Map.class);
	}

	@Test
	public void componentScanFunction() {
		create(ComponentScanConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("function")).isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookupFunction("function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("function")))
				.isAssignableFrom(Map.class);
	}

	@Test
	public void componentScanJarFunction() {
		try {
			create("greeter.jar", ComponentScanJarConfiguration.class);
			assertThat(context.getBean("greeter")).isInstanceOf(Function.class);
			assertThat(catalog.lookupFunction("greeter")).isInstanceOf(Function.class);
			assertThat(inspector.getInputType(catalog.lookupFunction("greeter")))
					.isAssignableFrom(String.class);
			assertThat(inspector.getInputWrapper(catalog.lookupFunction("greeter")))
					.isAssignableFrom(String.class);
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
		assertThat(inspector.getInputType(catalog.lookupFunction("other")))
				.isEqualTo(String.class);
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

	@Test
	public void compiledFunction() throws Exception {
		create(EmptyConfiguration.class,
				"spring.cloud.function.compile.foos.lambda=v -> v.toUpperCase()",
				"spring.cloud.function.compile.foos.inputType=String",
				"spring.cloud.function.compile.foos.outputType=String");
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("foos")).isInstanceOf(Function.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("foos")))
				.isEqualTo(String.class);
	}

	@Test
	public void byteCodeFunction() throws Exception {
		CompiledFunctionFactory<Function<String, String>> compiled = new FunctionCompiler<String, String>(
				String.class.getName()).compile("foos", "v -> v.toUpperCase()", "String",
						"String");
		FileSystemResource resource = new FileSystemResource("target/foos.fun");
		StreamUtils.copy(compiled.getGeneratedClassBytes(), resource.getOutputStream());
		create(EmptyConfiguration.class,
				"spring.cloud.function.import.foos.location=file:./target/foos.fun");
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat(catalog.lookupFunction("foos")).isInstanceOf(Function.class);
		assertThat(inspector.getInputWrapper(catalog.lookupFunction("foos")))
				.isEqualTo(String.class);
	}

	@Test
	public void compiledConsumer() throws Exception {
		create(EmptyConfiguration.class,
				"spring.cloud.function.compile.foos.lambda=" + getClass().getName()
						+ "::set",
				"spring.cloud.function.compile.foos.type=consumer",
				"spring.cloud.function.compile.foos.inputType=String");
		assertThat(catalog.lookupConsumer("foos")).isInstanceOf(Consumer.class);
		assertThat(inspector.getInputWrapper(catalog.lookupConsumer("foos")))
				.isEqualTo(String.class);
		@SuppressWarnings("unchecked")
		Consumer<String> consumer = (Consumer<String>) context.getBean("foos");
		consumer.accept("hello");
		assertThat(ContextFunctionCatalogAutoConfigurationTests.value).isEqualTo("hello");
	}

	@Test
	public void compiledFluxConsumer() throws Exception {
		create(EmptyConfiguration.class,
				"spring.cloud.function.compile.foos.lambda=f -> f.subscribe("
						+ getClass().getName() + "::set)",
				"spring.cloud.function.compile.foos.type=consumer");
		assertThat(catalog.lookupConsumer("foos")).isInstanceOf(Consumer.class);
		assertThat(inspector.getInputWrapper(catalog.lookupConsumer("foos")))
				.isEqualTo(Flux.class);
		@SuppressWarnings("unchecked")
		Consumer<Flux<String>> consumer = (Consumer<Flux<String>>) context
				.getBean("foos");
		consumer.accept(Flux.just("hello"));
		assertThat(ContextFunctionCatalogAutoConfigurationTests.value).isEqualTo("hello");
	}

	private void create(Class<?> type, String... props) {
		create(new Class<?>[] { type }, props);
	}

	private void create(Class<?>[] types, String... props) {
		context = new SpringApplicationBuilder((Object[]) types).properties(props).run();
		catalog = context.getBean(FunctionCatalog.class);
		inspector = context.getBean(FunctionInspector.class);
	}

	public static void set(Object value) {
		ContextFunctionCatalogAutoConfigurationTests.value = value.toString();
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
			return value -> list.add(value);
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class AmbiguousConfiguration {
		private List<Foo> list = new ArrayList<>();

		@Bean
		public Function<String, Foo> foos() {
			return value -> new Foo(value.toUpperCase());
		}

		@Bean
		@Qualifier("foos")
		public Consumer<Foo> consumer() {
			return value -> list.add(value);
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

	protected static class SingletonFunction implements Function<Integer, String> {

		@Override
		public String apply(Integer input) {
			return "value=" + input;
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
	@FunctionScan
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
			return new FunctionRegistration<Function<String, String>>(function())
					.name("other");
		}

		@Bean
		public Function<String, String> function() {
			return value -> value.toUpperCase();
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
