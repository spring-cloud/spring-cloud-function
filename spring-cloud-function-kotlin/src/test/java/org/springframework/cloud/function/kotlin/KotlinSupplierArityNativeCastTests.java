/*
 * Copyright 2025-2025 the original author or authors.
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

import java.util.function.Supplier;

import kotlin.jvm.functions.Function0;
import kotlinx.coroutines.flow.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.kotlin.arity.KotlinArityApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that Kotlin Supplier implementations can be cast to native Java functional interfaces,
 * invoked properly, and produce correct results.
 *
 * @author AI Assistant
 */
public class KotlinSupplierArityNativeCastTests {

	private GenericApplicationContext context;

	private FunctionCatalog catalog;

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	/**
	 * Test that plain suppliers from KotlinSupplierArityBean, KotlinAritySupplierComponent, and KotlinSupplierArityJava
	 * can be cast to java.util.function.Supplier and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"supplierPlain", "supplierKotlinPlain", "supplierJavaPlain"
	})
	public void testPlainSuppliersCastToNative(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to java.util.function.Supplier
		@SuppressWarnings("unchecked")
		Supplier<Integer> supplier = (Supplier<Integer>) supplierBean;

		// Invoke the supplier
		Integer result = supplier.get();

		// Verify the result (should be 42 based on the implementation)
		assertThat(result).isEqualTo(42);
	}

	/**
	 * Test that Mono suppliers can be cast to java.util.function.Supplier and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
			"supplierMono", "supplierKotlinMono", "supplierJavaMono"
	})
	public void testMonoSuppliersCastToNative(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to java.util.function.Supplier
		@SuppressWarnings("unchecked")
		Supplier<Mono<String>> supplier = (Supplier<Mono<String>>) supplierBean;

		// Invoke the supplier
		Mono<String> result = supplier.get();

		// Verify the result
		String value = result.block();
		assertThat(value).isEqualTo("Hello from Mono");
	}

	/**
	 * Test that Flux suppliers can be cast to java.util.function.Supplier and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
			"supplierFlux", "supplierKotlinFlux", "supplierJavaFlux"
	})
	public void testFluxSuppliersCastToNative(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to java.util.function.Supplier
		@SuppressWarnings("unchecked")
		Supplier<Flux<String>> supplier = (Supplier<Flux<String>>) supplierBean;

		// Invoke the supplier
		Flux<String> result = supplier.get();

		// Verify the result
		assertThat(result.collectList().block()).containsExactly("Alpha", "Beta", "Gamma");
	}

	/**
	 * Test that Message suppliers can be cast to java.util.function.Supplier and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
			"supplierMessage", "supplierKotlinMessage", "supplierJavaMessage"
	})
	public void testMessageSuppliersCastToNative(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to java.util.function.Supplier
		@SuppressWarnings("unchecked")
		Supplier<Message<String>> supplier = (Supplier<Message<String>>) supplierBean;

		// Invoke the supplier
		Message<String> result = supplier.get();

		// Verify the result
		assertThat(result.getPayload()).isEqualTo("Hello from Message");
		assertThat(result.getHeaders().get("messageId")).isNotNull();
	}

	/**
	 * Test that Mono&lt;Message&gt; suppliers can be cast to java.util.function.Supplier and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"supplierMonoMessage", "supplierKotlinMonoMessage", "supplierJavaMonoMessage"
	})
	public void testMonoMessageSuppliersCastToNative(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to java.util.function.Supplier
		@SuppressWarnings("unchecked")
		Supplier<Mono<Message<String>>> supplier = (Supplier<Mono<Message<String>>>) supplierBean;

		// Invoke the supplier
		Mono<Message<String>> result = supplier.get();

		// Verify the result
		Message<String> message = result.block();
		assertThat(message).isNotNull();
		assertThat(message.getPayload()).isEqualTo("Hello from Mono Message");
		assertThat(message.getHeaders().get("monoMessageId")).isNotNull();
		assertThat(message.getHeaders().get("source")).isEqualTo("mono");
	}

	/**
	 * Test that Flux&lt;Message&gt; suppliers can be cast to java.util.function.Supplier and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"supplierFluxMessage", "supplierKotlinFluxMessage", "supplierJavaFluxMessage"
	})
	public void testFluxMessageSuppliersCastToNative(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to java.util.function.Supplier
		@SuppressWarnings("unchecked")
		Supplier<Flux<Message<String>>> supplier = (Supplier<Flux<Message<String>>>) supplierBean;

		// Invoke the supplier
		Flux<Message<String>> result = supplier.get();

		// Verify the result
		assertThat(result.collectList().block()).hasSize(2);
		assertThat(result.map(msg -> msg.getPayload()).collectList().block())
			.containsExactly("Msg1", "Msg2");
	}

	/**
	 * Test that Flow suppliers can be cast to java.util.function.Supplier.
	 * Note: We can't easily invoke Flow suppliers directly in Java, but we can verify the cast works.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"supplierFlow", "supplierKotlinFlow", "supplierJavaFlow"
	})
	public void testFlowSuppliersCastToNative(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to java.util.function.Supplier
		@SuppressWarnings("unchecked")
		Supplier<Flow<String>> supplier = (Supplier<Flow<String>>) supplierBean;

		// Verify the cast works
		assertThat(supplier).isNotNull();
	}

	/**
	 * Test that Flow&lt;Message&gt; suppliers can be cast to java.util.function.Supplier.
	 * Note: We can't easily invoke Flow suppliers directly in Java, but we can verify the cast works.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"supplierFlowMessage", "supplierKotlinFlowMessage", "supplierJavaFlowMessage"
	})
	public void testFlowMessageSuppliersCastToNative(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to java.util.function.Supplier
		@SuppressWarnings("unchecked")
		Supplier<Flow<Message<String>>> supplier = (Supplier<Flow<Message<String>>>) supplierBean;

		// Verify the cast works
		assertThat(supplier).isNotNull();
	}

	/**
	 * Test that plain suppliers can be cast to kotlin.jvm.functions.Function0 and invoked.
	 * This verifies that Supplier implementations also implement the Kotlin Function0 interface.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"supplierPlain", "supplierKotlinPlain"
	})
	public void testPlainSuppliersCastToKotlinFunction0(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to kotlin.jvm.functions.Function0
		@SuppressWarnings("unchecked")
		Function0<Integer> supplier = (Function0<Integer>) supplierBean;

		// Invoke the supplier
		Integer result = supplier.invoke();

		// Verify the result (should be 42 based on the implementation)
		assertThat(result).isEqualTo(42);
	}

	/**
	 * Test that Mono suppliers can be cast to kotlin.jvm.functions.Function0 and invoked.
	 * This verifies that Supplier implementations also implement the Kotlin Function0 interface.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"supplierMono", "supplierKotlinMono"
	})
	public void testMonoSuppliersCastToKotlinFunction0(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to kotlin.jvm.functions.Function0
		@SuppressWarnings("unchecked")
		Function0<Mono<String>> supplier = (Function0<Mono<String>>) supplierBean;

		// Invoke the supplier
		Mono<String> result = supplier.invoke();

		// Verify the result
		String value = result.block();
		assertThat(value).isEqualTo("Hello from Mono");
	}

	/**
	 * Test that Message suppliers can be cast to kotlin.jvm.functions.Function0 and invoked.
	 * This verifies that Supplier implementations also implement the Kotlin Function0 interface.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"supplierMessage", "supplierKotlinMessage"
	})
	public void testMessageSuppliersCastToKotlinFunction0(String supplierName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper wrapper = this.catalog.lookup(supplierName);
		assertThat(wrapper).as("Supplier not found: " + supplierName).isNotNull();

		// Get the actual supplier bean
		Object supplierBean = wrapper.getTarget();
		assertThat(supplierBean).isNotNull();

		// Cast to kotlin.jvm.functions.Function0
		@SuppressWarnings("unchecked")
		Function0<Message<String>> supplier = (Function0<Message<String>>) supplierBean;

		// Invoke the supplier
		Message<String> result = supplier.invoke();

		// Verify the result
		assertThat(result.getPayload()).isEqualTo("Hello from Message");
		assertThat(result.getHeaders().get("messageId")).isNotNull();
	}

	private void create(Class<?>[] types, String... props) {
		this.context = (GenericApplicationContext) new SpringApplicationBuilder(types).properties(props).run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
	}
}
