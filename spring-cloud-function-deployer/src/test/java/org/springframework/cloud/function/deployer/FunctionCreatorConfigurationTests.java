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

package org.springframework.cloud.function.deployer;

import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.system.JavaVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { FunctionDeployerConfiguration.class })
@DirtiesContext
public abstract class FunctionCreatorConfigurationTests {

	@Autowired
	protected FunctionCatalog catalog;

	@EnableAutoConfiguration
	@TestPropertySource(properties = { "function.location=file:target/test-classes",
			"function.bean=org.springframework.cloud.function.test.Doubler" })
	public static class SingleFunctionTests extends FunctionCreatorConfigurationTests {

		@Test
		public void testDouble() {
			Function<Flux<Integer>, Flux<Integer>> function = catalog
					.lookup(Function.class, "function0");
			assertThat(function.apply(Flux.just(2)).blockFirst()).isEqualTo(4);
		}
	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = {
			"function.location=app:classpath,file:target/test-classes,file:target/test-classes/app",
			"function.bean=myDoubler" })
	public static class SingleFunctionWithAutoMainTests
			extends FunctionCreatorConfigurationTests {

		@Test
		public void testDouble() {
			Function<Flux<Integer>, Flux<Integer>> function = catalog
					.lookup(Function.class, "function0");
			assertThat(function.apply(Flux.just(2)).blockFirst()).isEqualTo(4);
		}
	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = {
			"function.location=app:classpath,file:target/test-classes,file:target/test-classes/app",
			"function.bean=myDoubler",
			"function.main=org.springframework.cloud.function.test.FunctionApp" })
	public static class SingleFunctionWithMainTests
			extends FunctionCreatorConfigurationTests {

		@Test
		public void testDouble() {
			Function<Flux<Integer>, Flux<Integer>> function = catalog
					.lookup(Function.class, "function0");
			assertThat(function.apply(Flux.just(2)).blockFirst()).isEqualTo(4);
		}
	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = {
			"function.location=app:classpath,file:target/test-classes,file:target/test-classes/app",
			"function.bean=doubler",
			"function.main=org.springframework.cloud.function.test.FunctionRegistrar" })
	public static class SingleFunctionWithRegistrarTests
			extends FunctionCreatorConfigurationTests {

		@Test
		public void testDouble() {
			Function<Flux<Integer>, Flux<Integer>> function = catalog
					.lookup(Function.class, "function0");
			assertThat(function.apply(Flux.just(2)).blockFirst()).isEqualTo(4);
		}

	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = {
			"function.location=app:classpath,file:target/test-classes,file:target/test-classes/app",
			"function.bean=frenchizer",
			"function.main=org.springframework.cloud.function.test.FunctionRegistrar" })
	public static class SingleFunctionWithRegistrarAndRegistrationTests
			extends FunctionCreatorConfigurationTests {

		@Test
		public void testFrenchize() {
			Function<Flux<Integer>, Flux<String>> function = catalog
					.lookup(Function.class, "function0");
			assertThat(function.apply(Flux.just(2)).blockFirst()).isEqualTo("deux");
		}
	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = {
			"function.location=app:classpath,file:target/test-classes,file:target/test-classes/app",
			"function.bean=myDoubler",
			"function.main=org.springframework.cloud.function.test.FunctionInitializer" })
	public static class SingleFunctionWithInitializerTests
			extends FunctionCreatorConfigurationTests {

		@Test
		public void testDouble() {
			Function<Flux<Integer>, Flux<Integer>> function = catalog
					.lookup(Function.class, "function0");
			assertThat(function.apply(Flux.just(2)).blockFirst()).isEqualTo(4);
		}
	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = { "function.location=app:classpath",
			"function.bean=org.springframework.cloud.function.test.SpringDoubler" })
	public static class ManualSpringFunctionTests
			extends FunctionCreatorConfigurationTests {

		@Test
		public void testDouble() {
			Function<Flux<Integer>, Flux<Integer>> function = catalog
					.lookup(Function.class, "function0");
			assertThat(function.apply(Flux.just(2)).blockFirst()).isEqualTo(4);
		}
	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = { "function.location=file:target/test-classes",
			"function.bean=org.springframework.cloud.function.test.NumberEmitter,"
					+ "org.springframework.cloud.function.test.Frenchizer" })
	public static class SupplierCompositionTests
			extends FunctionCreatorConfigurationTests {

		@BeforeClass
		public static void init() {
			Assume.assumeTrue("Java > 8",
					JavaVersion.getJavaVersion().isOlderThan(JavaVersion.NINE));
		}

		@Test
		public void testSupplier() {
			Supplier<Integer> function = catalog.lookup(Supplier.class, "function0");
			assertThat(function).isNull();
		}

		@Test
		public void testFunction() {
			Supplier<Flux<String>> function = catalog.lookup(Supplier.class,
					"function0|function1");
			assertThat(function.get().blockFirst()).isEqualTo("un");
		}

	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = { "function.location=file:target/test-classes",
			"function.bean=org.springframework.cloud.function.test.Doubler,"
					+ "org.springframework.cloud.function.test.Frenchizer" })
	public static class FunctionCompositionTests
			extends FunctionCreatorConfigurationTests {

		@BeforeClass
		public static void init() {
			Assume.assumeTrue("Java > 8",
					JavaVersion.getJavaVersion().isOlderThan(JavaVersion.NINE));
		}

		@Test
		public void testFunction() {
			Function<Flux<Integer>, Flux<String>> function = catalog
					.lookup(Function.class, "function0|function1");
			assertThat(function.apply(Flux.just(2)).blockFirst()).isEqualTo("quatre");
		}

		@Test
		public void testThen() {
			Function<Integer, String> function = catalog.lookup(Function.class,
					"function1");
			assertThat(function).isNull();
		}
	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = { "function.location=file:target/test-classes",
			"function.bean=org.springframework.cloud.function.test.Frenchizer,"
					+ "org.springframework.cloud.function.test.Printer" })
	public static class ConsumerCompositionTests
			extends FunctionCreatorConfigurationTests {

		@BeforeClass
		public static void init() {
			Assume.assumeTrue("Java > 8",
					JavaVersion.getJavaVersion().isOlderThan(JavaVersion.NINE));
		}

		@Rule
		public OutputCapture capture = new OutputCapture();

		@Test
		public void testConsumer() {
			Function<Flux<Integer>, Mono<Void>> function = catalog.lookup(Function.class,
					"function0|function1");
			function.apply(Flux.just(2)).block();
			capture.expect(containsString("Seen deux"));
		}
	}
}
