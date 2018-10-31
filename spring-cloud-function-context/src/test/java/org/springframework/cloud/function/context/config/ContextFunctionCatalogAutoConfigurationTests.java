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

package org.springframework.cloud.function.context.config;

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

import org.junit.After;
import org.junit.Test;
import org.reactivestreams.Publisher;

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
import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionScan;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.inject.FooConfiguration;
import org.springframework.cloud.function.kotlin.KotlinLambdasConfiguration;
import org.springframework.cloud.function.scan.ScannedFunction;
import org.springframework.cloud.function.test.GenericFunction;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 * @author Artem Bilan
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
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(context.getBean("function2")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function,function2"))
				.isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> f = catalog.lookup(Function.class,
				"function,function2,function3");
		assertThat(f).isInstanceOf(Function.class);
		assertThat(f.apply(Flux.just("hello")).blockFirst())
				.isEqualTo("HELLOfunction2function3");
		assertThat(context.getBean("supplierFoo")).isInstanceOf(Supplier.class);
		assertThat((Supplier<?>)catalog.lookup(Supplier.class, "supplierFoo"))
				.isInstanceOf(Supplier.class);
		assertThat(context.getBean("supplier_Foo")).isInstanceOf(Supplier.class);
		assertThat((Supplier<?>)catalog.lookup(Supplier.class, "supplier_Foo"))
				.isInstanceOf(Supplier.class);
	}

	@Test
	public void ambiguousFunction() {
		create(AmbiguousConfiguration.class);
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat((Supplier<?>)catalog.lookup(Supplier.class, "foos"))
				.isInstanceOf(Supplier.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "foos")))
				.isEqualTo(String.class);
		assertThat(inspector.getOutputType(catalog.lookup(Supplier.class, "foos")))
				.isEqualTo(Foo.class);
	}

	@Test
	public void configurationFunction() {
		create(FunctionConfiguration.class);
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "foos"))
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
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "foos")))
				.isEqualTo(String.class);
	}

	@Test
	public void externalDependencyInjection() {
		create(ExternalDependencyConfiguration.class);
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "foos")))
				.isEqualTo(String.class);
	}

	@Test
	public void composedFunction() {
		create(MultipleConfiguration.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "foos,bars"))
				.isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "names,foos")).isNull();
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "foos,bars")))
				.isAssignableFrom(String.class);
		assertThat(inspector.getOutputType(catalog.lookup(Function.class, "foos,bars")))
				.isAssignableFrom(Bar.class);
	}

	@Test
	public void composedSupplier() {
		create(MultipleConfiguration.class);
		assertThat((Supplier<?>)catalog.lookup(Supplier.class, "names,foos"))
				.isInstanceOf(Supplier.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "names,foos")).isNull();
		assertThat(inspector.getOutputType(catalog.lookup(Supplier.class, "names,foos")))
				.isAssignableFrom(Foo.class);
		// The input type is the same as the input type of the first element in the chain
		assertThat(inspector.getInputType(catalog.lookup(Supplier.class, "names,foos")))
				.isAssignableFrom(Void.class);
	}

	@Test
	public void composedConsumer() {
		create(MultipleConfiguration.class);
		assertThat((Consumer<?>)catalog.lookup(Consumer.class, "foos,print")).isNull();
		assertThat((Function<?,?>)catalog.lookup(Function.class, "foos,print"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "foos,print")))
				.isAssignableFrom(String.class);
		// The output type is the same as the output type of the last element in the chain
		assertThat(inspector.getOutputType(catalog.lookup(Function.class, "foos,print")))
				.isAssignableFrom(Void.class);
	}

	@Test
	public void genericFunction() {
		create(GenericConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Map.class);
	}

	@Test
	public void fluxMessageFunction() {
		create(FluxMessageConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.isMessage(catalog.lookup(Function.class, "function")))
				.isTrue();
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(String.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Flux.class);
	}

	@Test
	public void publisherMessageFunction() {
		create(PublisherMessageConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.isMessage(catalog.lookup(Function.class, "function")))
				.isTrue();
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(String.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Publisher.class);
	}

	@Test
	public void monoFunction() {
		create(MonoConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.isMessage(catalog.lookup(Function.class, "function")))
				.isFalse();
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(String.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Flux.class);
		assertThat(inspector.getOutputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Mono.class);
	}

	@Test
	public void messageFunction() {
		create(MessageConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.isMessage(catalog.lookup(Function.class, "function")))
				.isTrue();
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(String.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(String.class);
	}

	@Test
	public void genericFluxFunction() {
		create(GenericFluxConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Flux.class);
	}

	@Test
	public void externalFunction() {
		create(ExternalConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Map.class);
	}

	@Test
	public void singletonFunction() {
		create(SingletonConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Integer.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Integer.class);
	}

	@Test
	public void singletonMessageFunction() {
		create(SingletonMessageConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Integer.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Integer.class);
		assertThat(inspector.isMessage(catalog.lookup(Function.class, "function")))
				.isTrue();
	}

	@Test
	public void nonParametericTypeFunction() {
		create(NonParametricTypeSingletonConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Integer.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Integer.class);
	}

	@Test
	public void componentScanBeanFunction() {
		create(ComponentScanBeanConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Map.class);
	}

	@Test
	public void componentScanFunction() {
		create(ComponentScanConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Map.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "function")))
				.isAssignableFrom(Map.class);
	}

	@Test
	public void componentScanJarFunction() {
		try {
			create("greeter.jar", ComponentScanJarConfiguration.class);
			assertThat(context.getBean("greeter")).isInstanceOf(Function.class);
			assertThat((Function<?,?>)catalog.lookup(Function.class, "greeter"))
					.isInstanceOf(Function.class);
			assertThat(inspector.getInputType(catalog.lookup(Function.class, "greeter")))
					.isAssignableFrom(String.class);
			assertThat(
					inspector.getInputWrapper(catalog.lookup(Function.class, "greeter")))
							.isAssignableFrom(String.class);
		}
		finally {
			ClassUtils.overrideThreadContextClassLoader(getClass().getClassLoader());
		}
	}

	@Test
	public void kotlinLambdas() {
		create(new Class[] {KotlinLambdasConfiguration.class, SimpleConfiguration.class});

		assertThat(context.getBean("kotlinFunction")).isInstanceOf(Function.class);
		assertThat(context.getBean("kotlinFunction")).isInstanceOf(Function1.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "kotlinFunction"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "kotlinFunction")))
				.isAssignableFrom(String.class);
		assertThat(inspector.getOutputType(catalog.lookup(Function.class, "kotlinFunction")))
				.isAssignableFrom(String.class);

		assertThat(context.getBean("kotlinConsumer")).isInstanceOf(Consumer.class);
		assertThat(context.getBean("kotlinConsumer")).isInstanceOf(Function1.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "kotlinConsumer"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "kotlinConsumer")))
				.isAssignableFrom(String.class);

		assertThat(context.getBean("kotlinSupplier")).isInstanceOf(Supplier.class);
		assertThat(context.getBean("kotlinSupplier")).isInstanceOf(Function0.class);
		Supplier<Flux<String>> supplier = catalog.lookup(Supplier.class, "kotlinSupplier");
		assertThat(supplier.get().blockFirst()).isEqualTo("Hello");
		assertThat((Supplier<?>)catalog.lookup(Supplier.class, "kotlinSupplier"))
				.isInstanceOf(Supplier.class);
		assertThat(inspector.getOutputType(catalog.lookup(Supplier.class, "kotlinSupplier")))
				.isAssignableFrom(String.class);

		Function<Flux<String>, Flux<String>> function = catalog.lookup(Function.class, "kotlinFunction|function2");
		assertThat(function.apply(Flux.just("Hello")).blockFirst()).isEqualTo("HELLOfunction2");
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
		Object bean = context.getBean("function");
		assertThat(bean).isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> function = catalog.lookup(Function.class,
				"function");
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO");
		assertThat(bean).isNotSameAs(function);
		assertThat(inspector.getRegistration(bean)).isNotNull();
		assertThat(inspector.getRegistration(bean).getType())
				.isEqualTo(inspector.getRegistration(function).getType());
	}

	@Test
	public void simpleSupplier() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("supplier")).isInstanceOf(Supplier.class);
		Supplier<Flux<String>> supplier = catalog.lookup(Supplier.class, "supplier");
		assertThat(supplier.get().blockFirst()).isEqualTo("hello");
	}

	@Test
	public void simpleConsumer() {
		create(SimpleConfiguration.class);
		assertThat(context.getBean("consumer")).isInstanceOf(Consumer.class);
		Function<Flux<String>, Mono<Void>> consumer = catalog.lookup(Function.class,
				"consumer");
		consumer.apply(Flux.just("foo", "bar")).subscribe();
		assertThat(context.getBean(SimpleConfiguration.class).list).hasSize(2);
	}

	@Test
	public void qualifiedBean() {
		create(QualifiedConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function")).isNull();
		assertThat((Function<?,?>)catalog.lookup(Function.class, "other"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputType(catalog.lookup(Function.class, "other")))
				.isEqualTo(String.class);
	}

	@Test
	public void aliasBean() {
		create(AliasConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isNotNull();
		assertThat((Function<?,?>)catalog.lookup(Function.class, "other"))
				.isInstanceOf(Function.class);
	}

	@Test
	public void registrationBean() {
		create(RegistrationConfiguration.class);
		assertThat(context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function")).isNull();
		assertThat((Function<?,?>)catalog.lookup(Function.class, "registration"))
				.isNull();
		assertThat((Function<?,?>)catalog.lookup(Function.class, "other"))
				.isInstanceOf(Function.class);
	}

	@Test
	public void compiledFunction() throws Exception {
		create(EmptyConfiguration.class,
				"spring.cloud.function.compile.foos.lambda=v -> v.toUpperCase()",
				"spring.cloud.function.compile.foos.inputType=String",
				"spring.cloud.function.compile.foos.outputType=String");
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "foos")))
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
				"spring.cloud.function.imports.foos.location=file:./target/foos.fun");
		assertThat(context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "foos")))
				.isEqualTo(String.class);
	}

	@Test
	public void compiledConsumer() throws Exception {
		create(EmptyConfiguration.class,
				"spring.cloud.function.compile.foos.lambda=" + getClass().getName()
						+ "::set",
				"spring.cloud.function.compile.foos.type=consumer",
				"spring.cloud.function.compile.foos.inputType=String");
		assertThat((Function<?,?>)catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Function.class, "foos")))
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
		assertThat((Consumer<?>)catalog.lookup(Consumer.class, "foos"))
				.isInstanceOf(Consumer.class);
		assertThat(inspector.getInputWrapper(catalog.lookup(Consumer.class, "foos")))
				.isEqualTo(Flux.class);
		@SuppressWarnings("unchecked")
		Consumer<Flux<String>> consumer = (Consumer<Flux<String>>) context
				.getBean("foos");
		consumer.accept(Flux.just("hello"));
		assertThat(ContextFunctionCatalogAutoConfigurationTests.value).isEqualTo("hello");
	}

	@Test
	public void factoryBeanFunction() {
		create(FactoryBeanConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?,?>)catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> f = this.catalog.lookup(Function.class,
				"function");
		assertThat(f.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO-bar");
	}

	private void create(Class<?> type, String... props) {
		create(new Class<?>[] { type }, props);
	}

	private void create(Class<?>[] types, String... props) {
		context = new SpringApplicationBuilder((Class[]) types).properties(props).run();
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
			return new FunctionRegistration<Function<String, String>>(function(), "other");
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
