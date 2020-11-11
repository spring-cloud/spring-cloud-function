/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.function.context.catalog;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class BeanFactoryAwarePojoFunctionRegistryTests {

	private FunctionCatalog configureCatalog() {
		ApplicationContext context = new SpringApplicationBuilder(SampleFunctionConfiguration.class)
				.run("--logging.level.org.springframework.cloud.function=DEBUG");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	@Test
	public void testWithPojoFunctionImplementingFunction() {
		FunctionCatalog catalog = this.configureCatalog();

//		MyFunction f1 = catalog.lookup("myFunction");
//		assertThat(f1.uppercase("foo")).isEqualTo("FOO");

		Function<String, String> f2 = catalog.lookup("myFunction");
		assertThat(f2.apply("foo")).isEqualTo("FOO");

		Function<Integer, String> f2conversion = catalog.lookup("myFunction");
		assertThat(f2conversion.apply(123)).isEqualTo("123");

		Function<Message<String>, String> f2message = catalog.lookup("myFunction");
		assertThat(f2message.apply(MessageBuilder.withPayload("message").build())).isEqualTo("MESSAGE");

		Function<Flux<String>, Flux<String>> f3 = catalog.lookup("myFunction");
		assertThat(f3.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO");

		Function<Message<String>, Message<byte[]>> f2messageReturned = catalog.lookup("myFunction", "application/json");
		assertThat(new String(f2messageReturned.apply(MessageBuilder.withPayload("message").build()).getPayload())).isEqualTo("\"MESSAGE\"");
	}

	@Test
	public void testWithPojoFunction() {
		FunctionCatalog catalog = this.configureCatalog();

//		MyFunctionLike f1 = catalog.lookup("myFunctionLike");
//		assertThat(f1.uppercase("foo")).isEqualTo("FOO");

		Function<String, String> f2 = catalog.lookup("myFunctionLike");
		assertThat(f2.apply("foo")).isEqualTo("FOO");

		Function<Integer, String> f2conversion = catalog.lookup("myFunctionLike");
		assertThat(f2conversion.apply(123)).isEqualTo("123");

		Function<Message<String>, String> f2message = catalog.lookup("myFunctionLike");
		assertThat(f2message.apply(MessageBuilder.withPayload("message").build())).isEqualTo("MESSAGE");

		Function<Flux<String>, Flux<String>> f3 = catalog.lookup("myFunctionLike");
		assertThat(f3.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO");

		Function<Message<String>, Message<byte[]>> f2messageReturned = catalog.lookup("myFunctionLike", "application/json");
		assertThat(new String(f2messageReturned.apply(MessageBuilder.withPayload("message").build()).getPayload())).isEqualTo("\"MESSAGE\"");
	}

	@Test
	public void testWithPojoFunctionComposition() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<String, String> f1 = catalog.lookup("myFunction|myFunctionLike|func");
		assertThat(f1.apply("foo")).isEqualTo("FOO");
	}


	@EnableAutoConfiguration
	@Configuration
	protected static class SampleFunctionConfiguration {

		@Bean
		public MyFunction myFunction() {
			return new MyFunction();
		}

		@Bean
		public MyFunctionLike myFunctionLike() {
			return new MyFunctionLike();
		}

		@Bean
		public Function<String, String> func() {
			return v -> v;
		}
	}

	// POJO Function that implements Function
	private static class MyFunction implements Function<String, String> {
		public String uppercase(String value) {
			return value.toUpperCase();
		}

		@Override
		public String apply(String t) {
			return this.uppercase(t);
		}
	}

	// POJO Function
	private static class MyFunctionLike {
		public String uppercase(String value) {
			return value.toUpperCase();
		}
	}
}
