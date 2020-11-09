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

package org.springframework.cloud.function.userissues;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;


import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class UserIssuesTests {

	private FunctionCatalog configureCatalog(Class<?>... configClass) {
		ApplicationContext context = new SpringApplicationBuilder(configClass).run(
				"--logging.level.org.springframework.cloud.function=DEBUG", "--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	@BeforeEach
	public void before() {
		System.clearProperty("spring.cloud.function.definition");
	}

	@Test
	public void testIssue602() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(Issue602Configuration.class);
		Function<Message<String>, Integer> function = catalog.lookup("consumer");
		int result = function.apply(
				new GenericMessage<String>("[{\"name\":\"julien\"},{\"name\":\"ricky\"},{\"name\":\"bubbles\"}]"));
		assertThat(result).isEqualTo(3);
	}

	@Test
	public void testIssue602asPOJO() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(Issue602Configuration.class);
		Function<Message<List<Product>>, Integer> function = catalog.lookup("consumer");
		ArrayList<Product> products = new ArrayList<>();
		Product p = new Product();
		p.setName("julien");
		products.add(p);
		p = new Product();
		p.setName("ricky");
		products.add(p);
		p = new Product();
		p.setName("bubbles");
		products.add(p);
		int result = function.apply(new GenericMessage<List<Product>>(products));
		assertThat(result).isEqualTo(3);

	}

	@Test
	public void testIssue602asCollectionOfUnconvertedItems() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(Issue602Configuration.class);
		Function<Message<List<String>>, Integer> function = catalog.lookup("consumer");
		ArrayList<String> products = new ArrayList<>();
		products.add("{\"name\":\"julien\"}");
		products.add("{\"name\":\"ricky\"}");
		products.add("{\"name\":\"bubbles\"}");
		int result = function.apply(new GenericMessage<List<String>>(products));
		assertThat(result).isEqualTo(3);

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testIssue601() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(Issue601Configuration.class);
		FunctionInvocationWrapper function = catalog.lookup("uppercase");
		assertThat(function.getInputType().getTypeName())
				.isEqualTo(ResolvableType.forClassWithGenerics(Flux.class, String.class).getType().getTypeName());
		assertThat(function.getOutputType().getTypeName())
				.isEqualTo(ResolvableType.forClassWithGenerics(Flux.class, Integer.class).getType().getTypeName());
		Flux<Integer> result = (Flux<Integer>) function.apply(Flux.just("julien", "ricky", "bubbles"));
		List<Integer> results = result.collectList().block();
		assertThat(results.get(0)).isEqualTo(6);
		assertThat(results.get(1)).isEqualTo(5);
		assertThat(results.get(2)).isEqualTo(7);
	}

	@EnableAutoConfiguration
	@Configuration
	public static class Issue602Configuration {
		@Bean
		public Function<List<Product>, Integer> consumer() {
			return v -> {
				assertThat(v.get(0).getName()).isEqualTo("julien");
				assertThat(v.get(1).getName()).isEqualTo("ricky");
				assertThat(v.get(2).getName()).isEqualTo("bubbles");
				return v.size();
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class Issue601Configuration {
		@Bean
		public Uppercase uppercase() {
			return new Uppercase();
		}
	}

	public static class Uppercase implements Function<Flux<String>, Flux<Integer>> {

		@Override
		public Flux<Integer> apply(Flux<String> s) {
			return s.map(v -> v.length());
		}
	}

	public static class Product {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
