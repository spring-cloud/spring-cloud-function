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

package org.springframework.cloud.function.deployer;

import java.util.function.Function;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.system.JavaVersion;
import org.springframework.cloud.function.context.FunctionCatalog;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableFunctionDeployer
public class SpringFunctionFluxConfigurationTests {

	private Object catalog;

	private ApplicationBootstrap bootstrap;

	@Before
	public void run() {
		Assume.assumeTrue("Java > 8",
				JavaVersion.getJavaVersion().isOlderThan(JavaVersion.NINE));
		if (bootstrap == null) {
			bootstrap = new ApplicationBootstrap();
			bootstrap.run(SpringFunctionFluxConfigurationTests.class,
					"--function.location=file:target/it/flux/target/dependency",
					"--function.bean=foos",
					"--function.main=com.example.functions.FunctionApp");
			catalog = bootstrap.getRunner().getBean(FunctionCatalog.class.getName());
		}
	}

	@After
	public void close() {
		if (bootstrap != null) {
			bootstrap.close();
		}
	}

	// @TestPropertySource(properties = { "",
	// "function.main=com.example.functions.FunctionApp" })
	// public static class SourceTests extends SpringFunctionFluxConfigurationTests {

	@Test
	public void test() throws Exception {
		@SuppressWarnings("unchecked")
		Function<Flux<Foo>, Flux<Foo>> function = (Function<Flux<Foo>, Flux<Foo>>) bootstrap
				.getRunner()
				.evaluate("lookup(T(java.util.function.Function), 'function0')", catalog);
		assertThat(function.apply(Flux.just(new Foo("foo"))).blockFirst().getValue())
				.isEqualTo("FOO");
	}

}

class Foo {

	private String value;

	public Foo() {
	}

	public Foo(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "Foo [value=" + this.value + "]";
	}

}