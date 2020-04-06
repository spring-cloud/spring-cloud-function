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

import org.junit.Test;

import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.springframework.cloud.function.adapter.gcloud.integration.LocalServerTestSupport.verify;

/**
 * Integration tests for GCF Http Functions.
 *
 * @author Daniel Zou
 * @author Mike Eltsufin
 */
public class HttpFunctionIntegrationTest {

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
