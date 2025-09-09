/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.function.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.cloud.function.context.catalog.FunctionAroundWrapper;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;


/**
 * @author Marcin Grzejszczak
 * @author Oleg Zhurakousky
 * @since 4.0.0
 */
public class ObservationFunctionAroundWrapper extends FunctionAroundWrapper {
	private final ObservationRegistry observationRegistry;

	private final FunctionObservationConvention functionObservationConvention;

	public ObservationFunctionAroundWrapper(ObservationRegistry observationRegistry, @Nullable FunctionObservationConvention functionObservationConvention) {
		this.observationRegistry = observationRegistry;
		this.functionObservationConvention = functionObservationConvention;
	}

	@Override
	protected Object doApply(Object message, SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		return nonReactorStream((Message<?>) message, targetFunction);
	}

	private Object nonReactorStream(Message<?> message,
		SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		return functionProcessingObservation(targetFunction, message).observe(() -> targetFunction.apply(message));
	}

	private Observation functionProcessingObservation(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction, Message<?> message) {
		return FunctionObservation.FUNCTION_PROCESSING_OBSERVATION.observation(this.functionObservationConvention, DefaultFunctionObservationConvention.INSTANCE, () -> new FunctionContext(targetFunction, message), this.observationRegistry);
	}
}
