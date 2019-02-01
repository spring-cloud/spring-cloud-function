/*
 * Copyright 2012-2019 the original author or authors.
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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.junit.After;
import org.junit.Test;
import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 */
public class ContextFunctionCatalogAutoConfigurationKotlinTests {

	private ConfigurableApplicationContext context;

	private FunctionCatalog catalog;

	private FunctionInspector inspector;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void kotlinLambdas() {
		create(new Class[] { KotlinLambdasConfiguration.class,
				SimpleConfiguration.class });

		assertThat(this.context.getBean("kotlinFunction")).isInstanceOf(Function.class);
		assertThat(this.context.getBean("kotlinFunction")).isInstanceOf(Function1.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "kotlinFunction"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "kotlinFunction")))
						.isAssignableFrom(String.class);
		assertThat(this.inspector
				.getOutputType(this.catalog.lookup(Function.class, "kotlinFunction")))
						.isAssignableFrom(String.class);

		assertThat(this.context.getBean("kotlinConsumer")).isInstanceOf(Consumer.class);
		assertThat(this.context.getBean("kotlinConsumer")).isInstanceOf(Function1.class);
		assertThat((Function<?, ?>) this.catalog.lookup(Function.class, "kotlinConsumer"))
				.isInstanceOf(Function.class);
		assertThat(this.inspector
				.getInputType(this.catalog.lookup(Function.class, "kotlinConsumer")))
						.isAssignableFrom(String.class);

		assertThat(this.context.getBean("kotlinSupplier")).isInstanceOf(Supplier.class);
		assertThat(this.context.getBean("kotlinSupplier")).isInstanceOf(Function0.class);
		Supplier<Flux<String>> supplier = this.catalog.lookup(Supplier.class,
				"kotlinSupplier");
		assertThat(supplier.get().blockFirst()).isEqualTo("Hello");
		assertThat((Supplier<?>) this.catalog.lookup(Supplier.class, "kotlinSupplier"))
				.isInstanceOf(Supplier.class);
		assertThat(this.inspector
				.getOutputType(this.catalog.lookup(Supplier.class, "kotlinSupplier")))
						.isAssignableFrom(String.class);

		Function<Flux<String>, Flux<String>> function = this.catalog
				.lookup(Function.class, "kotlinFunction|function2");
		assertThat(function.apply(Flux.just("Hello")).blockFirst())
				.isEqualTo("HELLOfunction2");
	}

	private void create(Class<?>[] types, String... props) {
		this.context = new SpringApplicationBuilder(types).properties(props).run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
		this.inspector = this.context.getBean(FunctionInspector.class);
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
