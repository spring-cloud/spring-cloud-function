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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.kotlin.arity.KotlinArityApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for different arity suppliers in the FunctionCatalog.
 *
 * @author Adrien Poupard
 */
public class SupplierArityCatalogueTests {

	private GenericApplicationContext context;

	private FunctionCatalog catalog;

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierPlain", "supplierJavaPlain", "supplierKotlinPlain"
	})
	public void testPlainSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Plain supplier returns Int/Integer
		String outputTypeName = supplier.getOutputType().getTypeName();
		assertThat(outputTypeName).isEqualTo("java.lang.Integer");

		// Verify supplier execution
		Object result = supplier.get();
		assertThat(result).isEqualTo(42);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierFlow", "supplierJavaFlow", "supplierKotlinFlow"
	})
	public void testFlowSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Flow supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		// Output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(outputTypeName.contains("Flux")).isTrue();

		// Verify supplier execution returns a Flow/Flux
		Object result = supplier.get();
		assertThat(result.toString()).contains("Flux");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierSuspendPlain", "supplierKotlinSuspendPlain"
	})
	public void testSuspendPlainSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Suspend plain supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		// Suspend functions are wrapped in Flux by Spring Cloud Function
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("String")).isTrue();

		// Verify supplier execution returns a Flux
		Object result = supplier.get();
		assertThat(result.toString()).contains("Flux");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierSuspendFlow", "supplierKotlinSuspendFlow"
	})
	public void testSuspendFlowSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Suspend flow supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		// Output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(outputTypeName.contains("Flux")).isTrue();

		// Verify supplier execution returns a Flow/Flux
		Object result = supplier.get();
		assertThat(result.toString()).contains("Flux");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierMono", "supplierJavaMono", "supplierKotlinMono"
	})
	public void testMonoSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Mono supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		assertThat(outputTypeName.contains("Mono")).isTrue();

		// Verify supplier execution returns a Mono
		Object result = supplier.get();
		assertThat(result.toString()).contains("Mono");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierFlux", "supplierJavaFlux", "supplierKotlinFlux"
	})
	public void testFluxSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Flux supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		assertThat(outputTypeName.contains("Flux")).isTrue();

		// Verify supplier execution returns a Flux
		Object result = supplier.get();
		assertThat(result.toString()).contains("Flux");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierMessage", "supplierJavaMessage", "supplierKotlinMessage"
	})
	public void testMessageSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Message supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		assertThat(outputTypeName.contains("Message")).isTrue();

		// Verify supplier execution returns a Message
		Object result = supplier.get();
		assertThat(result).isInstanceOf(Message.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierMonoMessage", "supplierJavaMonoMessage", "supplierKotlinMonoMessage"
	})
	public void testMonoMessageSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Mono message supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		assertThat(outputTypeName.contains("Mono")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();

		// Verify supplier execution returns a Mono
		Object result = supplier.get();
		assertThat(result.toString()).contains("Mono");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierSuspendMessage", "supplierKotlinSuspendMessage"
	})
	public void testSuspendMessageSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Suspend message supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		// Suspend functions are wrapped in Flux by Spring Cloud Function
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();

		// Verify supplier execution returns a Flux
		Object result = supplier.get();
		assertThat(result.toString()).contains("Flux");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierFluxMessage", "supplierJavaFluxMessage", "supplierKotlinFluxMessage"
	})
	public void testFluxMessageSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Flux message supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();

		// Verify supplier execution returns a Flux
		Object result = supplier.get();
		assertThat(result.toString()).contains("Flux");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierFlowMessage", "supplierJavaFlowMessage", "supplierKotlinFlowMessage"
	})
	public void testFlowMessageSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Flow message supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		// Output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();

		// Verify supplier execution returns a Flow/Flux
		Object result = supplier.get();
		assertThat(result.toString()).contains("Flux");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"supplierSuspendFlowMessage", "supplierKotlinSuspendFlowMessage"
	})
	public void testSuspendFlowMessageSuppliers(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper supplier = this.catalog.lookup(supplierName);

		// Test should fail if supplier is not found
		assertThat(supplier).as("Supplier not found: " + supplierName).isNotNull();

		// Verify it's a supplier
		assertThat(supplier.isSupplier()).isTrue();

		// Suspend flow message supplier
		String outputTypeName = supplier.getOutputType().getTypeName();
		// Output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();

		// Verify supplier execution returns a Flow/Flux
		Object result = supplier.get();
		assertThat(result.toString()).contains("Flux");
	}

	private void create(Class<?>[] types, String... props) {
		this.context = (GenericApplicationContext) new SpringApplicationBuilder(types).properties(props).run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
	}
}
