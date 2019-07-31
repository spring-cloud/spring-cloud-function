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

package org.springframework.cloud.function.deployer;

import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FunctionDeployerConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
		"function.location=file:target/it/support/target/dependency" })
public abstract class SpringFunctionAppExplodedConfigurationTests {

	@Autowired
	protected FunctionCatalog catalog;

	@EnableAutoConfiguration
	@TestPropertySource(properties = { "function.bean=myEmitter",
			"function.main=com.example.functions.FunctionApp" })
	public static class SourceTests extends SpringFunctionAppExplodedConfigurationTests {

		@Test
		@Ignore
		public void test() throws Exception {
			Supplier<String> function = this.catalog.lookup(Supplier.class,
					"function0");
			assertThat(function.get()).isEqualTo("one");
		}

	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = { "function.bean=myEmitter,myCounter" })
	public static class CompositeTests
			extends SpringFunctionAppExplodedConfigurationTests {

		@Test
		@Ignore
		public void test() throws Exception {
			Supplier<Integer> function = this.catalog.lookup(Supplier.class,
					"function0|function1");
			assertThat(function.get()).isEqualTo(3);
		}

	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = { "function.bean=myCounter" })
	public static class ProcessorTests
			extends SpringFunctionAppExplodedConfigurationTests {

		@Test
		@Ignore
		public void test() throws Exception {
			Function<Flux<String>, Flux<Integer>> function = this.catalog
					.lookup(Function.class, "function0");
			assertThat(function.apply(Flux.just("spam")).blockFirst()).isEqualTo(4);
		}

	}

	@EnableAutoConfiguration
	@TestPropertySource(properties = { "function.bean=myDoubler" })
	@Ignore // @TestPropertySource is not taken into account nor it is visible
	public static class SinkTests extends SpringFunctionAppExplodedConfigurationTests {

		@Rule
		public OutputCapture capture = new OutputCapture();

		@Test
		public void test() throws Exception {
			// Can't assert side effects.
			Function<Flux<Integer>, Mono<Void>> function = this.catalog
					.lookup(Function.class, "function0");
			function.apply(Flux.just(5)).block();
			this.capture.expect(containsString(String.format("10%n")));
		}

	}

}
