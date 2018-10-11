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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.system.JavaVersion;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableFunctionDeployer
public class SpringFunctionFluxConfigurationTests {

	private Object catalog;

	private Object inspector;

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
			inspector = bootstrap.getRunner().getBean(FunctionInspector.class.getName());
		}
	}

	@After
	public void close() {
		if (bootstrap != null) {
			bootstrap.close();
		}
	}

	@Test
	public void test() throws Exception {
		@SuppressWarnings("unchecked")
		Function<Object, Object> function = (Function<Object, Object>) bootstrap
				.getRunner()
				.evaluate("lookup(T(java.util.function.Function), 'function0')", catalog);
		assertThat(function).isNotNull();
		Class<?> inputType = (Class<?>) bootstrap.getRunner()
				.evaluate("getInputType(#function)", inspector, "function", function);
		assertThat(inputType.getName()).isEqualTo("com.example.functions.Foo");
		Object foo = create(inputType);
		Class<?> outputType = (Class<?>) bootstrap.getRunner()
				.evaluate("getOutputType(#function)", inspector, "function", function);
		assertThat(outputType.getName()).isEqualTo("com.example.functions.Foo");
		String value = (String) bootstrap.getRunner().evaluate(
				"apply(T(reactor.core.publisher.Flux).just(#foo)).blockFirst().getValue()",
				function, "foo", foo);
		assertThat(value).isEqualTo("FOO");
	}

	private Object create(Class<?> inputType) throws InstantiationException,
			IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Constructor<?> constructor = inputType.getConstructor(String.class);
		constructor.setAccessible(true);
		return constructor.newInstance("foo");
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