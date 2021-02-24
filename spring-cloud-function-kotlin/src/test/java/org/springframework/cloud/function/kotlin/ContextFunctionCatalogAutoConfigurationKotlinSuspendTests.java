/*
 * Copyright 2019-2021 the original author or authors.
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

package org.springframework.cloud.function.kotlin;

import java.lang.reflect.ParameterizedType;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import kotlin.jvm.functions.Function2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Adrien Poupard
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ContextFunctionCatalogAutoConfigurationKotlinSuspendTests {

	private GenericApplicationContext context;

	private FunctionCatalog catalog;

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void typeDiscoveryTests() {
		create(new Class[] { KotlinSuspendFlowLambdasConfiguration.class,
			ContextFunctionCatalogAutoConfigurationKotlinTests.SimpleConfiguration.class });

		Object function = this.context.getBean("kotlinFunction");
		ParameterizedType functionType = (ParameterizedType) FunctionTypeUtils.discoverFunctionType(function, "kotlinFunction", this.context);
		assertThat(functionType.getRawType().getTypeName()).isEqualTo(Function.class.getName());
		assertThat(functionType.getActualTypeArguments().length).isEqualTo(2);
		assertThat(functionType.getActualTypeArguments()[0].getTypeName()).isEqualTo("reactor.core.publisher.Flux<java.lang.String>");
		assertThat(functionType.getActualTypeArguments()[1].getTypeName()).isEqualTo("reactor.core.publisher.Flux<java.lang.String>");

		function = this.context.getBean("kotlinConsumer");
		functionType = (ParameterizedType) FunctionTypeUtils.discoverFunctionType(function, "kotlinConsumer", this.context);
		assertThat(functionType.getRawType().getTypeName()).isEqualTo(Consumer.class.getName());
		assertThat(functionType.getActualTypeArguments().length).isEqualTo(1);
		assertThat(functionType.getActualTypeArguments()[0].getTypeName()).isEqualTo("reactor.core.publisher.Flux<java.lang.String>");

		function = this.context.getBean("kotlinSupplier");
		functionType = (ParameterizedType) FunctionTypeUtils.discoverFunctionType(function, "kotlinSupplier", this.context);
		assertThat(functionType.getRawType().getTypeName()).isEqualTo(Supplier.class.getName());
		assertThat(functionType.getActualTypeArguments().length).isEqualTo(1);
		assertThat(functionType.getActualTypeArguments()[0].getTypeName()).isEqualTo("reactor.core.publisher.Flux<java.lang.String>");

		function = this.context.getBean("kotlinPojoFunction");
		functionType = (ParameterizedType) FunctionTypeUtils.discoverFunctionType(function, "kotlinPojoFunction", this.context);
		assertThat(functionType.getRawType().getTypeName()).isEqualTo(Function.class.getName());
		assertThat(functionType.getActualTypeArguments().length).isEqualTo(2);
		assertThat(functionType.getActualTypeArguments()[0].getTypeName()).isEqualTo("reactor.core.publisher.Flux<org.springframework.cloud.function.kotlin.Person>");
		assertThat(functionType.getActualTypeArguments()[1].getTypeName()).isEqualTo("reactor.core.publisher.Flux<java.lang.String>");
	}

	@Test
	public void shouldNotLoadKotlinSuspendLambasNotUsingFlow() {
		create(new Class[] { KotlinSuspendLambdasConfiguration.class,
			ContextFunctionCatalogAutoConfigurationKotlinTests.SimpleConfiguration.class });

		assertThat(this.context.getBean("kotlinFunction")).isInstanceOf(Function2.class);
		assertThatThrownBy(() -> {
			this.catalog.lookup(Function.class, "kotlinFunction");
		}).isInstanceOf(BeanCreationException.class);

		assertThatThrownBy(() -> {
			this.catalog.lookup(Function.class, "kotlinConsumer");
		}).isInstanceOf(BeanCreationException.class);

		assertThatThrownBy(() -> {
			this.catalog.lookup(Supplier.class, "kotlinSupplier");
		}).isInstanceOf(BeanCreationException.class);

	}

	private void create(Class<?>[] types, String... props) {
		this.context = (GenericApplicationContext) new SpringApplicationBuilder(types).properties(props).run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
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
