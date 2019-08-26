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
import org.junit.Ignore;
import org.junit.Test;
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
import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Artem Bilan
 * @author Oleg Zhurakousky
 * @author Anshul Mehra
 */
public class ContextFunctionCatalogAutoConfigurationTests {

	private static String value;

	private ConfigurableApplicationContext context;

	private FunctionCatalog catalog;

	private FunctionInspector inspector;

	public static void set(Object value) {
		ContextFunctionCatalogAutoConfigurationTests.value = value.toString();
	}

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
		ContextFunctionCatalogAutoConfigurationTests.value = null;
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
//		assertThat((Supplier<?>) this.catalog.lookup(Supplier.class, "supplierFoo"))
//				.isInstanceOf(Supplier.class);
//		assertThat(this.context.getBean("supplier_Foo")).isInstanceOf(Supplier.class);
//		assertThat((Supplier<?>) this.catalog.lookup(Supplier.class, "supplier_Foo"))
//				.isInstanceOf(Supplier.class);
	}

	@Test
	@Ignore
	// do we really need this test and behavior? What does this even mean?
	public void ambiguousFunction() {
		create(AmbiguousConfiguration.class);
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat((Supplier<?>) this.catalog.lookup(Supplier.class, "foos"))
				.isInstanceOf(Supplier.class);
		assertThat(
				this.inspector.getInputType(this.catalog.lookup(Function.class, "foos")))
						.isEqualTo(String.class);
		assertThat(
				this.inspector.getOutputType(this.catalog.lookup(Supplier.class, "foos")))
						.isEqualTo(Foo.class);
	}

	@Test
	@Ignore
	public void configurationFunction() {
		create(FunctionConfiguration.class);
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(
				this.inspector.getInputType(this.catalog.lookup(Function.class, "foos")))
						.isEqualTo(String.class);
		assertThat(
				this.inspector.getOutputType(this.catalog.lookup(Function.class, "foos")))
						.isEqualTo(Foo.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "foos")))
						.isEqualTo(Flux.class);
	}

	@Test
	public void dependencyInjection() {
		create(DependencyInjectionConfiguration.class);
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(
				this.inspector.getInputType(this.catalog.lookup(Function.class, "foos")))
						.isEqualTo(String.class);
	}

	@Test
	public void externalDependencyInjection() {
		create(ExternalDependencyConfiguration.class);
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(
				this.inspector.getInputType(this.catalog.lookup(Function.class, "foos")))
						.isEqualTo(String.class);
	}

	@Test
	public void composedFunction() {
		create(MultipleConfiguration.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos,bars"))
				.isInstanceOf(Function.class);
//		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "names,foos"))
//				.isNull();
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "foos,bars")))
						.isAssignableFrom(String.class);
		assertThat(this.inspector
				.getOutputType(this.catalog.lookup(Function.class, "foos,bars")))
						.isAssignableFrom(Bar.class);
	}

	@Test
	public void composedSupplier() {
		create(MultipleConfiguration.class);
		assertThat((Supplier<?>) this.catalog.lookup(Supplier.class, "names,foos"))
				.isInstanceOf(Supplier.class);
//		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "names,foos"))
//				.isNull();
		assertThat(this.inspector
				.getOutputType(this.catalog.lookup(Supplier.class, "names,foos")))
						.isAssignableFrom(Foo.class);
		// The input type is the same as the input type of the first element in the chain
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Supplier.class, "names,foos")))
						.isAssignableFrom(Void.class);
	}

	@Test
	public void composedConsumer() {
		create(MultipleConfiguration.class);
		assertThat((Consumer<?>) this.catalog.lookup(Consumer.class, "foos,print"))
			.isInstanceOf(Consumer.class);
//				.isNull();
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos,print"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "foos,print")))
						.isAssignableFrom(String.class);
		// The output type is the same as the output type of the last element in the chain
		assertThat(this.inspector
				.getOutputType(this.catalog.lookup(Function.class, "foos,print")))
						.isAssignableFrom(Void.class);
	}

	@Test
	public void genericFunction() {
		create(GenericConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Map.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Map.class);
	}

	@Test
	public void fluxMessageFunction() {
		create(FluxMessageConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(
				this.inspector.isMessage(this.catalog.lookup(Function.class, "function")))
						.isTrue();
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(String.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Flux.class);
	}

	@Test
	public void publisherMessageFunction() {
		create(PublisherMessageConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(
				this.inspector.isMessage(this.catalog.lookup(Function.class, "function")))
						.isTrue();
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(String.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Publisher.class);
	}

	@Test
	public void monoFunction() {
		create(MonoConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(
				this.inspector.isMessage(this.catalog.lookup(Function.class, "function")))
						.isFalse();
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(String.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Flux.class);
		assertThat(this.inspector
				.getOutputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Mono.class);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test//(expected = IllegalArgumentException.class)
	public void monoToMonoNonVoidFunction() {
		create(MonoToMonoNonVoidConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(String.class);
		assertThat(this.inspector
				.getOutputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(String.class);

		Function function = this.context.getBean(FunctionCatalog.class).lookup("function");
		Object result = ((Mono) function.apply(Mono.just("flux"))).block();
		System.out.println(result);
	}

	@Test
	public void messageFunction() {
		create(MessageConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(
				this.inspector.isMessage(this.catalog.lookup(Function.class, "function")))
						.isTrue();
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(String.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(String.class);
	}

	@Test
	public void genericFluxFunction() {
		create(GenericFluxConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Map.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Flux.class);
	}

	@Test
	public void externalFunction() {
		create(ExternalConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Map.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Map.class);
	}

	@Test
	public void singletonFunction() {
		create(SingletonConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Integer.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Integer.class);
	}

	@Test
	@Ignore
	public void singletonMessageFunction() {
		create(SingletonMessageConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Integer.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Integer.class);
		assertThat(
				this.inspector.isMessage(this.catalog.lookup(Function.class, "function")))
						.isTrue();
	}

	@Test
	public void nonParametericTypeFunction() {
		create(NonParametricTypeSingletonConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Integer.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Integer.class);
	}

	@Test
	public void componentScanBeanFunction() {
		create(ComponentScanBeanConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Map.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Map.class);
	}

	@Test
	public void componentScanFunction() {
		create(ComponentScanConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Map.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "function")))
						.isAssignableFrom(Map.class);
	}

	@Test
	public void componentScanJarFunction() {
		try {
			create("greeter.jar", ComponentScanJarConfiguration.class);
			assertThat(this.context.getBean("greeter")).isInstanceOf(Function.class);
			assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "greeter"))
					.isInstanceOf(Function.class);
			assertThat(this.inspector
					.getInputType(this.catalog.lookup(Function.class, "greeter")))
							.isAssignableFrom(String.class);
			assertThat(this.inspector
					.getInputWrapper(this.catalog.lookup(Function.class, "greeter")))
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
	public void simpleFunction() {
		create(SimpleConfiguration.class);
		Object bean = this.context.getBean("function");
		assertThat(bean).isInstanceOf(Function.class);
		Function<Flux<String>, Flux<String>> function = this.catalog
				.lookup(Function.class, "function");
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO");
		assertThat(bean).isNotSameAs(function);
		assertThat(this.inspector.getRegistration(function)).isNotNull();
		assertThat(this.inspector.getRegistration(function).getType())
				.isEqualTo(this.inspector.getRegistration(function).getType());
	}

	@Test
	@Ignore
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
	@Ignore
	public void qualifiedBean() {
		create(QualifiedConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
				.isNull();
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "other"))
				.isInstanceOf(Function.class);
		assertThat(
				this.inspector.getInputType(this.catalog.lookup(Function.class, "other")))
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
	@Ignore
	public void registrationBean() {
		create(RegistrationConfiguration.class);
		assertThat(this.context.getBean("function")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "function"))
		.isInstanceOf(Function.class);
//				.isNull();
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "registration"))
		.isInstanceOf(Function.class);
//				.isNull();
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "other"))
				.isInstanceOf(Function.class);
	}

	@Test
	public void compiledFunction() throws Exception {
		create(EmptyConfiguration.class,
				"spring.cloud.function.compile.foos.lambda=v -> v.toUpperCase()",
				"spring.cloud.function.compile.foos.inputType=String",
				"spring.cloud.function.compile.foos.outputType=String");
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "foos")))
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
		assertThat(this.context.getBean("foos")).isInstanceOf(Function.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "foos")))
						.isEqualTo(String.class);
	}

	@Test
	public void compiledConsumer() throws Exception {
		create(EmptyConfiguration.class,
				"spring.cloud.function.compile.foos.lambda=" + getClass().getName()
						+ "::set",
				"spring.cloud.function.compile.foos.type=consumer",
				"spring.cloud.function.compile.foos.inputType=String");
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "foos")))
						.isEqualTo(String.class);
		@SuppressWarnings("unchecked")
		Consumer<String> consumer = (Consumer<String>) this.context.getBean("foos");
		consumer.accept("hello");
		assertThat(ContextFunctionCatalogAutoConfigurationTests.value).isEqualTo("hello");
	}

	@Test
	public void compiledFluxConsumer() throws Exception {
		create(EmptyConfiguration.class,
				"spring.cloud.function.compile.foos.lambda=f -> f.subscribe("
						+ getClass().getName() + "::set)",
				"spring.cloud.function.compile.foos.type=consumer");
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "foos"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputWrapper(this.catalog.lookup(Function.class, "foos")))
						.isEqualTo(Flux.class);
		@SuppressWarnings("unchecked")
		Consumer<Flux<String>> consumer = (Consumer<Flux<String>>) this.context
				.getBean("foos");
		consumer.accept(Flux.just("hello"));
		assertThat(ContextFunctionCatalogAutoConfigurationTests.value).isEqualTo("hello");
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
		this.inspector = this.context.getBean(FunctionInspector.class);
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
			@Nullable FunctionRegistry functionCatalog, @Nullable FunctionInspector inspector) {
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
