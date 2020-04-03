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

package org.springframework.cloud.function.adapter.gcloud.integration;

import java.io.IOException;
import java.util.function.Function;

import org.junit.Rule;
import org.junit.Test;

import org.springframework.cloud.function.adapter.gcloud.FunctionInvoker;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for GCF Http Functions.
 *
 * @author Daniel Zou
 * @author Mike Eltsufin
 */
public class HttpFunctionIntegrationTest {

//	@Rule
//	public CloudFunctionServer cloudFunctionServer = new CloudFunctionServer(FunctionInvoker.class,
//			CloudFunctionMain.class);

	@Test
	public void test() {
		doTest(CloudFunctionMainSingular.class, null, "hello", "HELLO");
	}

	@Test
	public void testUppercase() throws InterruptedException, IOException {
		doTest(CloudFunctionMain.class, "uppercase", "hello", "HELLO");

	}

	@Test
	public void testFooBar() {
		doTest(CloudFunctionMain.class, "foobar", new Foo("Hi"), new Bar("Hi"));
	}

	private <I, O> void doTest(Class mainClass, String function, I input, O expectedOutput) {
		CloudFunctionServer2 cloudFunctionServer = new CloudFunctionServer2(
				mainClass, function);
		try {
			cloudFunctionServer.before();
			cloudFunctionServer.test(input, expectedOutput);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			cloudFunctionServer.after();
		}
	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class CloudFunctionMainSingular {

		@Bean
		public Function<String, String> uppercase() {
			return input -> input.toUpperCase();
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	public static class CloudFunctionMain {

		@Bean
		public Function<String, String> uppercase() {
			return input -> input.toUpperCase();
		}

		@Bean
		public Function<Foo, Bar> foobar() {
			return input -> new Bar(input.value);
		}

	}

	private static class Foo {

		String value;

		Foo(String value) {
			this.value = value;
		}

	}

	private static class Bar {

		String value;

		Bar(String value) {
			this.value = value;
		}

	}

}
