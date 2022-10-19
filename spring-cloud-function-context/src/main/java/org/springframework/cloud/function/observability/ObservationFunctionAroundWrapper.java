/*
 * Copyright 2012-2019 the original author or authors.
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.FunctionAroundWrapper;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;


/**
 * @author Marcin Grzejszczak
 * @author Oleg Zhurakousky
 * @since 4.0.0
 */
public class ObservationFunctionAroundWrapper extends FunctionAroundWrapper {

	private static final Log log = LogFactory.getLog(ObservationFunctionAroundWrapper.class);

	private final ObservationRegistry observationRegistry;

	private final FunctionReceiverObservationConvention functionReceiverObservationConvention;

	private final FunctionObservationConvention functionObservationConvention;

	private final FunctionSenderObservationConvention functionSenderObservationConvention;

	public ObservationFunctionAroundWrapper(ObservationRegistry observationRegistry, @Nullable FunctionReceiverObservationConvention functionReceiverObservationConvention, @Nullable FunctionObservationConvention functionObservationConvention, @Nullable FunctionSenderObservationConvention functionSenderObservationConvention) {
		this.observationRegistry = observationRegistry;
		this.functionReceiverObservationConvention = functionReceiverObservationConvention;
		this.functionObservationConvention = functionObservationConvention;
		this.functionSenderObservationConvention = functionSenderObservationConvention;
	}

	@Override
	protected Object doApply(Object message, SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		if (FunctionTypeUtils.isCollectionOfMessage(targetFunction.getOutputType())) {
			return targetFunction.apply(message); // no instrumentation
		}
		return nonReactorStream(message, targetFunction);
	}

	@SuppressWarnings("unchecked")
	private Object nonReactorStream(Object message,
		SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		if (targetFunction.isConsumer()) {
			Observation observationOfInputMessage = stoppedObservationOfInputMessage(message, targetFunction);
			Observation consumerObservation = consumerObservation(targetFunction, observationOfInputMessage);
			return consumerObservation.observe(() -> targetFunction.apply(message));
		}
		else if (targetFunction.isFunction()) {
			Observation observationOfInputMessage = stoppedObservationOfInputMessage(message, targetFunction);
			Observation consumerObservation = consumerObservation(targetFunction, observationOfInputMessage);
			Object outputMessage = consumerObservation.observe(() -> targetFunction.apply(message));
			if (isNonNullMessageType(outputMessage)) {
				return outputMessage; // no instrumentation
			}
			return observeOutputMessage(outputMessage, targetFunction, consumerObservation);
		}
		else {
			Object supplierOutputMessage = functionProcessingObservation(targetFunction).observe(targetFunction::get);
			if (isNonNullMessageType(supplierOutputMessage)) {
				return supplierOutputMessage; // no instrumentation
			}
			return observeOutputMessage(supplierOutputMessage, targetFunction, null);
		}
	}

	private Observation functionProcessingObservation(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		return FunctionObservation.FUNCTION_PROCESSING_OBSERVATION.observation(this.functionObservationConvention, DefaultFunctionObservationConvention.INSTANCE, () -> new FunctionContext(targetFunction), this.observationRegistry);
	}

	private Observation consumerObservation(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction, Observation observationOfInputMessage) {
		return functionProcessingObservation(targetFunction)
			.parentObservation(observationOfInputMessage);
	}

	private boolean isNonNullMessageType(Object outputMessage) {
		return outputMessage == null || !(outputMessage instanceof Message<?>);
	}

	/**
	 * Confirmation of getting of message from broker.
	 *
	 * @param message        message to process
	 * @param targetFunction target function
	 * @return stopped observation
	 */
	private Observation stoppedObservationOfInputMessage(Object message,
		SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		Observation consumerObservation = FunctionObservation.FUNCTION_CONSUMER_OBSERVATION.observation(this.functionReceiverObservationConvention, DefaultFunctionReceiverObservationConvention.INSTANCE, () -> new FunctionReceiverContext(targetFunction, (Message<?>) message), this.observationRegistry);
		consumerObservation.start().stop();
		return consumerObservation;
	}

	/**
	 * Enriching the output message.
	 *
	 * @param message        message to process
	 * @param targetFunction target function
	 * @return enriched output message
	 */
	private Message<?> observeOutputMessage(Object message,
		SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction, @Nullable Observation parentObservation) {
		FunctionSenderContext context = new FunctionSenderContext(targetFunction, MessageBuilder.fromMessage((Message<?>) message));
		FunctionObservation.FUNCTION_PRODUCER_OBSERVATION.observation(this.functionSenderObservationConvention, DefaultFunctionSenderObservationConvention.INSTANCE, () -> context, this.observationRegistry).parentObservation(parentObservation).start().stop();
		return context.getCarrier().build();
	}
}
