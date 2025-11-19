/*
 * Copyright 2019-present the original author or authors.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

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

		FunctionCatalog functionCatalog = this.context.getBean(FunctionCatalog.class);

		FunctionInvocationWrapper kotlinFunction = functionCatalog.lookup("kotlinFunction");
		assertThat(kotlinFunction.isFunction()).isTrue();
		assertThat(kotlinFunction.getInputType().getTypeName())
			.isEqualTo("reactor.core.publisher.Flux<java.lang.String>");
		assertThat(kotlinFunction.getOutputType().getTypeName())
			.isEqualTo("reactor.core.publisher.Flux<java.lang.String>");

		FunctionInvocationWrapper kotlinConsumer = functionCatalog.lookup("kotlinConsumer");
		assertThat(kotlinConsumer.isConsumer()).isTrue();
		assertThat(kotlinConsumer.getInputType().getTypeName())
			.isEqualTo("reactor.core.publisher.Flux<java.lang.String>");

		FunctionInvocationWrapper kotlinSupplier = functionCatalog.lookup("kotlinSupplier");
		assertThat(kotlinSupplier.isSupplier()).isTrue();
		assertThat(kotlinSupplier.getOutputType().getTypeName())
			.isEqualTo("reactor.core.publisher.Flux<java.lang.String>");

		FunctionInvocationWrapper kotlinPojoFunction = functionCatalog.lookup("kotlinPojoFunction");
		assertThat(kotlinPojoFunction.isFunction()).isTrue();
		assertThat(kotlinPojoFunction.getInputType().getTypeName())
			.isEqualTo("reactor.core.publisher.Flux<org.springframework.cloud.function.kotlin.Person>");
		assertThat(kotlinPojoFunction.getOutputType().getTypeName())
			.isEqualTo("reactor.core.publisher.Flux<java.lang.String>");
	}

	private void create(Class<?>[] types, String... props) {
		this.context = (GenericApplicationContext) new SpringApplicationBuilder(types).properties(props).run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
	}

}
