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

import java.util.Arrays;
import java.util.Collections;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.boot.loader.thin.ArchiveUtils;
import org.springframework.boot.loader.tools.LogbackInitializer;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

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
			id = deploy("maven://com.example:function-sample:1.0.0.BUILD-SNAPSHOT");
			// "--debug");
		}
	}

	@AfterClass
	public static void close() {
		if (id != null) {
			deployer.undeploy("sample");
		}
	}

	@Test
	public void deployAndExtractFunctions() throws Exception {
		// This one can only work if you change the boot classpath to contain reactor-core
		// and reactive-streams
		expected.expect(ClassCastException.class);
		@SuppressWarnings("unchecked")
		Flux<String> result = (Flux<String>) deployer.lookupFunction("uppercase")
				.apply(Flux.just("foo"));
		assertThat(result.blockFirst()).isEqualTo("FOO");
	}

	@Test
	public void deployAndExtractConsumers() throws Exception {
		assertThat(deployer.lookupConsumer("sink")).isNull();
	}

	@Test
	public void deployAndExtractSuppliers() throws Exception {
		assertThat(deployer.lookupSupplier("words")).isNotNull();
	}

	private static String deploy(String jarName, String... args) throws Exception {
		Resource resource = new FileSystemResource(
				ArchiveUtils.getArchiveRoot(ArchiveUtils.getArchive(jarName)));
		AppDefinition definition = new AppDefinition(resource.getFilename(),
				Collections.emptyMap());
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.emptyMap(), Arrays.asList(args));
		String deployed = deployer.deploy("sample", request);
		return deployed;
	}

}
