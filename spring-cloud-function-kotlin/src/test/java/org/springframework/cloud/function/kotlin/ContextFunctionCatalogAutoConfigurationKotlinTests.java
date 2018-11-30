/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.function.kotlin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import reactor.core.publisher.Flux;

/**
 * @author Oleg Zhurakousky
 */
public class ContextFunctionCatalogAutoConfigurationKotlinTests {

	private ConfigurableApplicationContext context;
	private FunctionCatalog catalog;
	private FunctionInspector inspector;

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void kotlinLambdas() {
		create(new Class[] {KotlinLambdasConfiguration.class, SimpleConfiguration.class});

		assertThat(context.getBean("kotlinFunction")).isInstanceOf(Function.class);
		assertThat(context.getBean("kotlinFunction")).isInstanceOf(Function1.class);
		assertThat((Function<?, ?>) catalog.lookup(Function.class, "kotlinFunction"))
				.isInstanceOf(Function.class);
		assertThat(
				inspector.getInputType(catalog.lookup(Function.class, "kotlinFunction")))
						.isAssignableFrom(String.class);
		assertThat(
				inspector.getOutputType(catalog.lookup(Function.class, "kotlinFunction")))
						.isAssignableFrom(String.class);

		assertThat(context.getBean("kotlinConsumer")).isInstanceOf(Consumer.class);
		assertThat(context.getBean("kotlinConsumer")).isInstanceOf(Function1.class);
		assertThat((Function<?, ?>) catalog.lookup(Function.class, "kotlinConsumer"))
				.isInstanceOf(Function.class);
		assertThat(
				inspector.getInputType(catalog.lookup(Function.class, "kotlinConsumer")))
						.isAssignableFrom(String.class);

		assertThat(context.getBean("kotlinSupplier")).isInstanceOf(Supplier.class);
		assertThat(context.getBean("kotlinSupplier")).isInstanceOf(Function0.class);
		Supplier<Flux<String>> supplier = catalog.lookup(Supplier.class,
				"kotlinSupplier");
		assertThat(supplier.get().blockFirst()).isEqualTo("Hello");
		assertThat((Supplier<?>) catalog.lookup(Supplier.class, "kotlinSupplier"))
				.isInstanceOf(Supplier.class);
		assertThat(
				inspector.getOutputType(catalog.lookup(Supplier.class, "kotlinSupplier")))
						.isAssignableFrom(String.class);

		Function<Flux<String>, Flux<String>> function = catalog.lookup(Function.class,
				"kotlinFunction|function2");
		assertThat(function.apply(Flux.just("Hello")).blockFirst())
				.isEqualTo("HELLOfunction2");
	}

	private void create(Class<?>[] types, String... props) {
		context = new SpringApplicationBuilder(types).properties(props).run();
		catalog = context.getBean(FunctionCatalog.class);
		inspector = context.getBean(FunctionInspector.class);
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SimpleConfiguration {

		@Bean
		public Function<String, String> function2() {
			return value -> value + "function2";
		}
	}
}
