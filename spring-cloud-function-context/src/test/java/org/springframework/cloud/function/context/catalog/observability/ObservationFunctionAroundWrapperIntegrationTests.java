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

package org.springframework.cloud.function.context.catalog.observability;

import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.observation.ObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.SampleTestRunner;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import io.micrometer.tracing.test.simple.SpanAssert;
import io.micrometer.tracing.test.simple.SpansAssert;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.cloud.function.context.catalog.observability.tracing.FunctionTracingObservationHandler;
import org.springframework.cloud.function.context.catalog.observability.tracing.MessageHeaderPropagatorGetter;
import org.springframework.cloud.function.context.catalog.observability.tracing.MessageHeaderPropagatorSetter;
import org.springframework.cloud.function.context.config.JsonMessageConverter;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationFunctionAroundWrapperIntegrationTests extends SampleTestRunner {

	CompositeMessageConverter messageConverter = new CompositeMessageConverter(
		Collections.singletonList(new JsonMessageConverter(new JacksonMapper(new ObjectMapper()))));

	SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(new DefaultConversionService(), messageConverter,
		new JacksonMapper(new ObjectMapper()));

	ObservationFunctionAroundWrapperIntegrationTests() {
		super(SampleRunnerConfig.builder().build(), new SimpleMeterRegistry().withTimerObservationHandler());
	}

	@Override
	public BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizeObservationHandlers() {
		return (buildingBlocks, observationHandlers) -> observationHandlers.addFirst(new FunctionTracingObservationHandler(buildingBlocks.getTracer(), testPropagator(buildingBlocks.getTracer()), new MessageHeaderPropagatorGetter(), new MessageHeaderPropagatorSetter()));
	}

	private Propagator testPropagator(Tracer tracer) {
		return new Propagator() {

			@Override
			public <C> void inject(TraceContext context, C carrier, Setter<C> setter) {
				setter.set(carrier, "superHeader", "test");
			}

			@Override
			public List<String> fields() {
				return Collections.singletonList("superHeader");
			}

			@Override
			public <C> Span.Builder extract(C carrier, Getter<C> getter) {
				return tracer.spanBuilder();
			}
		};
	}

	@Override
	public SampleTestRunnerConsumer yourCode() throws Exception {
		return (buildingBlocks, meterRegistry) -> {
			ObservationFunctionAroundWrapper wrapper = new ObservationFunctionAroundWrapper(meterRegistry);

			// TESTS

			test_tracing_with_function(wrapper, buildingBlocks);
		};
	}

	private void test_tracing_with_function(ObservationFunctionAroundWrapper wrapper, BuildingBlocks bb) {
		FunctionRegistration<GreeterFunction> registration = new FunctionRegistration<>(new GreeterFunction(),
			"greeter").type(FunctionTypeUtils.discoverFunctionTypeFromClass(GreeterFunction.class));
		catalog.register(registration);
		SimpleFunctionRegistry.FunctionInvocationWrapper function = catalog.lookup("greeter");

		Message<?> result = (Message<?>) wrapper
			.apply(MessageBuilder.withPayload("hello").setHeader("superHeader", "someValue").build(), function);

		assertThat(result.getPayload()).isEqualTo("HELLO");
		List<FinishedSpan> spans = bb.getFinishedSpans();
		SpansAssert.assertThat(spans)
			.haveSameTraceId()
			.hasSize(3);
		SpanAssert.assertThat(spans.get(0)).hasNameEqualTo("handle").isStarted();
		SpanAssert.assertThat(spans.get(1)).hasNameEqualTo("greeter").isStarted();
		SpanAssert.assertThat(spans.get(2)).hasNameEqualTo("send").isStarted();
	}


	private static class GreeterFunction implements Function<String, String> {

		@Override
		public String apply(String in) {
			return in.toUpperCase();
		}

	}
}
