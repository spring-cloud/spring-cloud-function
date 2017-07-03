/*
 * Copyright 2016-2017 the original author or authors.
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

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.boot.loader.tools.LogbackInitializer;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class FunctionExtractingFunctionCatalogTests {

	private static String id;

	static {
		LogbackInitializer.initialize();
	}

	private static FunctionExtractingFunctionCatalog deployer = new FunctionExtractingFunctionCatalog();

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Before
	public void init() throws Exception {
		if (id == null) {
			deploy("sample", "maven://com.example:function-sample:1.0.0.BUILD-SNAPSHOT");
			// "--debug");
			id = deploy("pojos",
					"maven://com.example:function-sample-pojo:1.0.0.BUILD-SNAPSHOT");
		}
	}

	@AfterClass
	public static void close() {
		if (id != null) {
			deployer.undeploy("sample");
			deployer.undeploy("pojos");
		}
	}

	@Test
	public void listFunctions() throws Exception {
		assertThat(deployer.getFunctionNames()).contains("sample/uppercase",
				"pojos/uppercase");
	}

	@Test
	public void nameFunction() throws Exception {
		assertThat(deployer.getName(deployer.lookupFunction("sample/uppercase")))
				.isEqualTo("sample/uppercase");
	}

	@Test
	public void deployAndExtractFunctions() throws Exception {
		// This one can only work if you change the boot classpath to contain reactor-core
		// and reactive-streams
		expected.expect(ClassCastException.class);
		@SuppressWarnings("unchecked")
		Flux<String> result = (Flux<String>) deployer.lookupFunction("pojos/uppercase")
				.apply(Flux.just("foo"));
		assertThat(result.blockFirst()).isEqualTo("FOO");
	}

	@Test
	public void listConsumers() throws Exception {
		assertThat(deployer.getConsumerNames()).isEmpty();
	}

	@Test
	public void deployAndExtractConsumers() throws Exception {
		assertThat(deployer.lookupConsumer("pojos/sink")).isNull();
	}

	@Test
	public void listSuppliers() throws Exception {
		assertThat(deployer.getSupplierNames()).contains("sample/words", "pojos/words");
	}

	@Test
	public void nameSupplier() throws Exception {
		assertThat(deployer.getName(deployer.lookupSupplier("sample/words")))
				.isEqualTo("sample/words");
	}

	@Test
	public void deployAndExtractSuppliers() throws Exception {
		assertThat(deployer.lookupSupplier("sample/words")).isNotNull();
		assertThat(deployer.lookupSupplier("pojos/words")).isNotNull();
	}

	private static String deploy(String name, String path, String... args)
			throws Exception {
		String deployed = deployer.deploy(name, path, args);
		return deployed;
	}

}
