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

import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 */
public class ContextFunctionCatalogAutoConfigurationKotlinTests {

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
		create(new Class[] { KotlinLambdasConfiguration.class,
				SimpleConfiguration.class });

		Object function = this.context.getBean("kotlinFunction");
		ParameterizedType functionType = (ParameterizedType) FunctionTypeUtils.discoverFunctionType(function, "kotlinFunction", this.context);
		assertThat(functionType.getRawType().getTypeName()).isEqualTo(Function.class.getName());
		assertThat(functionType.getActualTypeArguments().length).isEqualTo(2);
		assertThat(functionType.getActualTypeArguments()[0].getTypeName()).isEqualTo(String.class.getName());
		assertThat(functionType.getActualTypeArguments()[1].getTypeName()).isEqualTo(String.class.getName());

		function = this.context.getBean("kotlinConsumer");
		functionType = (ParameterizedType) FunctionTypeUtils.discoverFunctionType(function, "kotlinConsumer", this.context);
		assertThat(functionType.getRawType().getTypeName()).isEqualTo(Consumer.class.getName());
		assertThat(functionType.getActualTypeArguments().length).isEqualTo(1);
		assertThat(functionType.getActualTypeArguments()[0].getTypeName()).isEqualTo(String.class.getName());

		function = this.context.getBean("kotlinSupplier");
		functionType = (ParameterizedType) FunctionTypeUtils.discoverFunctionType(function, "kotlinSupplier", this.context);
		assertThat(functionType.getRawType().getTypeName()).isEqualTo(Supplier.class.getName());
		assertThat(functionType.getActualTypeArguments().length).isEqualTo(1);
		assertThat(functionType.getActualTypeArguments()[0].getTypeName()).isEqualTo(String.class.getName());

		function = this.context.getBean("kotlinPojoFunction");
		functionType = (ParameterizedType) FunctionTypeUtils.discoverFunctionType(function, "kotlinPojoFunction", this.context);
		assertThat(functionType.getRawType().getTypeName()).isEqualTo(Function.class.getName());
		assertThat(functionType.getActualTypeArguments().length).isEqualTo(2);
		assertThat(functionType.getActualTypeArguments()[0].getTypeName()).isEqualTo(Person.class.getName());
		assertThat(functionType.getActualTypeArguments()[1].getTypeName()).isEqualTo(String.class.getName());


		function = this.context.getBean("kotlinListPojoFunction");
		functionType = (ParameterizedType) FunctionTypeUtils.discoverFunctionType(function, "kotlinListPojoFunction", this.context);
		assertThat(functionType.getRawType().getTypeName()).isEqualTo(Function.class.getName());
		assertThat(functionType.getActualTypeArguments().length).isEqualTo(2);
		assertThat(functionType.getActualTypeArguments()[0].getTypeName()).isEqualTo("java.util.List<org.springframework.cloud.function.kotlin.Person>");
		assertThat(functionType.getActualTypeArguments()[1].getTypeName()).isEqualTo(String.class.getName());
	}

	@Test
	public void testWithComplexTypesAndRouting() {
		create(new Class[] { KotlinLambdasConfiguration.class,
				SimpleConfiguration.class });

		FunctionInvocationWrapper function = this.catalog.lookup("kotlinListPojoFunction");
		String result = (String) function.apply("[{\"name\":\"Ricky\"}]");
		assertThat(result).isEqualTo("List of: Ricky");

		function = this.catalog.lookup(Function.class, "functionRouter");
		result = (String) function.apply(MessageBuilder.withPayload("[{\"name\":\"Ricky\"}]")
				.setHeader("spring.cloud.function.definition", "kotlinListPojoFunction").build());
		assertThat(result).isEqualTo("List of: Ricky");

	}

	@Test
	public void kotlinLambdas() {
		create(new Class[] { KotlinLambdasConfiguration.class,
				SimpleConfiguration.class });

		assertThat(this.context.getBean("kotlinFunction")).isInstanceOf(Function1.class);
		FunctionInvocationWrapper function = this.catalog.lookup(Function.class, "kotlinFunction");
		assertThat(function).isInstanceOf(Function.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(function.getInputType()))).isAssignableFrom(String.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(function.getOutputType()))).isAssignableFrom(String.class);


		function = this.catalog.lookup(Function.class, "kotlinConsumer");
		assertThat(this.context.getBean("kotlinConsumer")).isInstanceOf(Function1.class);
		assertThat(function).isInstanceOf(Function.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(function.getInputType()))).isAssignableFrom(String.class);


		assertThat(this.context.getBean("kotlinSupplier")).isInstanceOf(Function0.class);
		FunctionInvocationWrapper supplier = this.catalog.lookup(Function.class, "kotlinSupplier");
		assertThat(supplier).isInstanceOf(Supplier.class);
		assertThat(supplier.get()).isEqualTo("Hello");
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(supplier.getOutputType()))).isAssignableFrom(String.class);

		function = this.catalog.lookup(Function.class, "kotlinFunction|function2");
		assertThat(function.apply("Hello")).isEqualTo("HELLOfunction2");

		Function<String, String> javaFunction = this.catalog
				.lookup(Function.class, "javaFunction");
		assertThat(javaFunction.apply("Hello"))
				.isEqualTo("Hello");
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
