/*
 * Copyright 2022-present the original author or authors.
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

package org.springframework.cloud.function.adapter.azure;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.function.adapter.azure.helper.TestExecutionContext;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.list;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link FunctionInvoker} custom result handling.
 *
 * @author Chris Bono
 */
class CustomFunctionInvokerTests {

	private FunctionInvoker<?, ?>  currentInvoker;

	@AfterEach
	void closeCurrentInvoker() {
		if (this.currentInvoker != null) {
			this.currentInvoker.close();
		}
	}

	/**
	 * Verifies custom result handling and proper post-process callback invocation for an imperative function.
	 */
	@Test
	void customImperativeResultHandling() {
		FunctionInvoker<String, String> invoker = new FunctionInvoker<String, String>(TestFunctionsConfig.class) {
			@Override
			protected String postProcessImperativeFunctionResult(String rawInputs, Object functionInputs,
				Object functionResult, FunctionInvocationWrapper function, ExecutionContext executionContext
			) {
				return functionResult + "+imperative";
			}
		};
		invoker = spyOnAndCloseAfterTest(invoker);
		ExecutionContext executionContext = new TestExecutionContext("imperativeUppercase");
		String result = invoker.handleRequest("foo", executionContext);
		assertThat(result).isEqualTo("FOO+imperative");

		// Below here verifies that the expected callback(s) were invoked w/ the expected arguments

		// Only imperative post-process callback should be called
		verify(invoker, never()).postProcessReactiveFunctionResult(anyString(), any(), any(Publisher.class), any(), same(executionContext));
		verify(invoker, never()).postProcessMonoFunctionResult(anyString(), any(), any(Mono.class), any(), same(executionContext));
		verify(invoker, never()).postProcessFluxFunctionResult(anyString(), any(), any(Flux.class), any(), same(executionContext));

		// Only sniff-test the payload of the input message (the other fields are problematic to verify and no value doing that here)
		ArgumentCaptor<GenericMessage> functionInputsCaptor = ArgumentCaptor.forClass(GenericMessage.class);
		verify(invoker).postProcessImperativeFunctionResult(eq("foo"), functionInputsCaptor.capture(), eq("FOO"), any(), same(executionContext));
		assertThat(functionInputsCaptor.getValue()).extracting(GenericMessage::getPayload).isEqualTo("foo");
	}

	/**
	 * Verifies custom result handling and proper post-process callback invocation for a reactive Mono function.
	 */
	@Test
	void customReactiveMonoResultHandling() {
		FunctionInvoker<String, String> invoker = new FunctionInvoker<String, String>(TestFunctionsConfig.class) {
			@Override
			protected String postProcessMonoFunctionResult(String rawInputs, Object functionInputs, Mono<?> functionResult,
				FunctionInvocationWrapper function, ExecutionContext executionContext
			) {
				return functionResult.block().toString() + "+mono";
			}
		};
		invoker = spyOnAndCloseAfterTest(invoker);
		ExecutionContext executionContext = new TestExecutionContext("reactiveMonoUppercase");
		String result = invoker.handleRequest("foo", executionContext);
		assertThat(result).isEqualTo("FOO+mono");

		// Below here verifies that the expected callback(s) were invoked w/ the expected arguments

		// Only publisher->mono post-process callbacks should be called
		verify(invoker, never()).postProcessImperativeFunctionResult(anyString(), any(), any(), any(), same(executionContext));
		verify(invoker, never()).postProcessFluxFunctionResult(anyString(), any(), any(Flux.class), any(), same(executionContext));

		// Only sniff-test the payload of the input message and the mono (the other fields are problematic to verify and no value doing that here)
		ArgumentCaptor<GenericMessage> functionInputsCaptor = ArgumentCaptor.forClass(GenericMessage.class);
		ArgumentCaptor<Mono> functionResultCaptor = ArgumentCaptor.forClass(Mono.class);
		verify(invoker).postProcessReactiveFunctionResult(eq("foo"), functionInputsCaptor.capture(), functionResultCaptor.capture(), any(), same(executionContext));
		verify(invoker).postProcessMonoFunctionResult(eq("foo"), functionInputsCaptor.capture(), functionResultCaptor.capture(), any(), same(executionContext));
		// NOTE: The captors get called twice as the args are just delegated from publisher->mono callback
		assertThat(functionInputsCaptor.getAllValues()).extracting(GenericMessage::getPayload).containsExactly("foo", "foo");
		assertThat(functionResultCaptor.getAllValues()).extracting(Mono::block).containsExactly("FOO", "FOO");
	}

	/**
	 * Verifies custom result handling and proper post-process callback invocation for a reactive Flux function.
	 */
	@Test
	void customReactiveFluxResultHandling() {
		FunctionInvoker<List<String>, String> invoker = new FunctionInvoker<List<String>, String>(TestFunctionsConfig.class) {
			@Override
			protected String postProcessFluxFunctionResult(List<String> rawInputs, Object functionInputs,
				Flux<?> functionResult, FunctionInvocationWrapper function, ExecutionContext executionContext
			) {
				return functionResult.map(o -> o.toString() + "+flux").collectList().block().stream().collect(Collectors.joining("/"));
			}
		};
		invoker = spyOnAndCloseAfterTest(invoker);
		ExecutionContext executionContext = new TestExecutionContext("reactiveFluxUppercase");
		List<String> rawInputs = Arrays.asList("foo", "bar");
		String result = invoker.handleRequest(rawInputs, executionContext);
		assertThat(result).isEqualTo("FOO+flux/BAR+flux");

		// Below here verifies that the expected callback(s) were invoked w/ the expected arguments

		// Only publisher->flux post-process callbacks should be called
		verify(invoker, never()).postProcessImperativeFunctionResult(anyList(), any(), any(), any(), same(executionContext));
		verify(invoker, never()).postProcessMonoFunctionResult(anyList(), any(), any(Mono.class), any(), same(executionContext));

		// Only sniff-test the payload of the input message and the mono (the other fields are problematic to verify and no value doing that here)
		ArgumentCaptor<Flux<GenericMessage>> functionInputsCaptor = ArgumentCaptor.forClass(Flux.class);
		ArgumentCaptor<Flux> functionResultCaptor = ArgumentCaptor.forClass(Flux.class);
		verify(invoker).postProcessReactiveFunctionResult(same(rawInputs), functionInputsCaptor.capture(), functionResultCaptor.capture(), any(), same(executionContext));
		verify(invoker).postProcessFluxFunctionResult(same(rawInputs), functionInputsCaptor.capture(), functionResultCaptor.capture(), any(), same(executionContext));

		// NOTE: The captors get called twice as the args are just delegated from publisher->flux callback

		// The functionInputs for each call is Flux<GreetingMessage> with 2 items - one for 'foo' and one for 'bar'
		assertThat(functionInputsCaptor.getAllValues())
			.extracting(Flux::collectList).extracting(Mono::block)
			.flatExtracting(fluxAsList -> fluxAsList.stream().collect(Collectors.toList()))
			.extracting(GenericMessage::getPayload).containsExactlyInAnyOrder("foo", "bar", "foo", "bar");

		// The functionResult for each call is a Flux<String> w/ 2 items { "FOO", "BAR" }
		assertThat(functionResultCaptor.getAllValues())
			.extracting(Flux::collectList).extracting(Mono::block)
			.containsExactlyInAnyOrder(list("FOO", "BAR"), list("FOO", "BAR"));
	}

	private <I, O> FunctionInvoker<I, O> spyOnAndCloseAfterTest(FunctionInvoker<I, O> invoker) {
		this.currentInvoker = invoker;
		return spy(invoker);
	}

	@Configuration
	@EnableAutoConfiguration
	static class TestFunctionsConfig {

		@Bean
		public Function<String, String> imperativeUppercase() {
			return (s) -> s.toUpperCase(Locale.ROOT);
		}

		@Bean
		public Function<Mono<String>, Mono<String>> reactiveMonoUppercase() {
			return (m) -> m.map(String::toUpperCase);
		}

		@Bean
		public Function<Flux<String>, Flux<String>> reactiveFluxUppercase() {
			return (f) -> f.map(String::toUpperCase);
		}

	}
}
