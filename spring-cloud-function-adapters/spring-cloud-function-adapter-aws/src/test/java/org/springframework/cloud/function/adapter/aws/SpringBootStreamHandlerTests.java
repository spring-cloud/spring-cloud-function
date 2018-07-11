/*
 * Copyright 2016-2018 the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.function.Function;

import org.junit.Test;

import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class SpringBootStreamHandlerTests {

	private SpringBootStreamHandler handler;

	@Test
	public void functionBeanWithJacksonConfig() throws Exception {
		handler = new SpringBootStreamHandler(FunctionConfigWithJackson.class);
		handler.initialize();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		handler.handleRequest(new ByteArrayInputStream("{\"value\":\"foo\"}".getBytes()),
				output, null);
		assertThat(output.toString()).isEqualTo("{\"value\":\"FOO\"}");
	}

	@Test
	public void functionBeanWithoutJacksonConfig() throws Exception {
		handler = new SpringBootStreamHandler(FunctionConfigWithoutJackson.class);
		handler.initialize();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		handler.handleRequest(new ByteArrayInputStream("{\"value\":\"foo\"}".getBytes()),
				output, null);
		assertThat(output.toString()).isEqualTo("{\"value\":\"FOO\"}");
	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class })
	protected static class FunctionConfigWithJackson {
		@Bean
		public Function<Foo, Bar> function() {
			return foo -> new Bar(foo.getValue().toUpperCase());
		}
	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class})
	protected static class FunctionConfigWithoutJackson {
		@Bean
		public Function<Foo, Bar> function() {
			return foo -> new Bar(foo.getValue().toUpperCase());
		}
	}

	protected static class Foo {
		private String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	protected static class Bar {
		private String value;

		public Bar() {
		}

		public Bar(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}
}
