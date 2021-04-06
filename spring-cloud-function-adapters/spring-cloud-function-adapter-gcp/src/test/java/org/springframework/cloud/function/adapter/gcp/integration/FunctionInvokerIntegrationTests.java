/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.function.adapter.gcp.integration;

import java.io.IOException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.function.adapter.gcp.integration.LocalServerTestSupport.verify;

/**
 * Integration tests for GCF Http Functions.
 *
 * @author Daniel Zou
 * @author Mike Eltsufin
 */
public class FunctionInvokerIntegrationTests {

	@Test
	public void testSingular() {
		verify(CloudFunctionMainSingular.class, null, "hello", "HELLO");
	}

	@Test
	public void testUppercase() throws InterruptedException, IOException {
		verify(CloudFunctionMain.class, "uppercase", "hello", "HELLO");

	}

	@Test
	public void testFooBar() {
		verify(CloudFunctionMain.class, "foobar", new Foo("Hi"), new Bar("Hi"));
	}

	@Test
	public void testErrorResponse() {
		try (LocalServerTestSupport.ServerProcess serverProcess =
						LocalServerTestSupport.startServer(ErrorFunction.class, "errorFunction")) {

			TestRestTemplate testRestTemplate = new TestRestTemplate();

			HttpHeaders headers = new HttpHeaders();
			ResponseEntity<String> response = testRestTemplate.postForEntity(
					"http://localhost:" + serverProcess.getPort(), new HttpEntity<>("test", headers),
					String.class);

			assertThat(response.getStatusCode().is5xxServerError()).isTrue();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * An example function which throws an error to test response code propagation.
	 */
	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	static class ErrorFunction {

		@Bean
		Supplier<String> errorFunction() {
			return () -> {
				throw new RuntimeException();
			};
		}
	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	static class CloudFunctionMainSingular {

		@Bean
		Function<String, String> uppercase() {
			return input -> input.toUpperCase();
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	static class CloudFunctionMain {

		@Bean
		Function<String, String> uppercase() {
			return input -> input.toUpperCase();
		}

		@Bean
		Function<Foo, Bar> foobar() {
			return input -> new Bar(input.value);
		}

	}

	public static class Foo {

		String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		public Foo() {
		}

		Foo(String value) {
			this.value = value;
		}

	}

	public static class Bar {

		String value;

		Bar(String value) {
			this.value = value;
		}

		Bar() {
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

}
