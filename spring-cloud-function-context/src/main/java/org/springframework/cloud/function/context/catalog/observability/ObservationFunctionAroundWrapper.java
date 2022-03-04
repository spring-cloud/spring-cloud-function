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

package org.springframework.cloud.function.context.catalog.observability;

import io.micrometer.core.instrument.observation.Observation;
import io.micrometer.core.instrument.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;

import org.springframework.cloud.function.context.catalog.FunctionAroundWrapper;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;

/**
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class ObservationFunctionAroundWrapper extends FunctionAroundWrapper implements Observation.TagsProviderAware<FunctionTagsProvider> {

	private static final Log log = LogFactory.getLog(ObservationFunctionAroundWrapper.class);

	private final ObservationRegistry observationRegistry;

	private FunctionTagsProvider tagsProvider = new DefaultFunctionTagsProvider();

	public ObservationFunctionAroundWrapper(ObservationRegistry observationRegistry) {
		this.observationRegistry = observationRegistry;
	}

	@Override
	protected Object doApply(Object message, SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		if (FunctionTypeUtils.isCollectionOfMessage(targetFunction.getOutputType())) {
			return targetFunction.apply(message); // no instrumentation
		}
		else if (targetFunction.isInputTypePublisher() || targetFunction.isOutputTypePublisher()) {
			return reactorStream((Publisher) message, targetFunction);
		}
		return nonReactorStream(message, targetFunction);
	}

	private Object reactorStream(Publisher message, SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		// TODO
		return message;
	}

	private Object nonReactorStream(Object message,
		SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		FunctionContext context = new FunctionContext(targetFunction).withInput(message);
		Object invocationMessage = context.getModifiedInput();
		Object result = Observation
			.createNotStarted(FunctionObservation.FUNCTION_OBSERVATION.getName(), context, this.observationRegistry)
			.contextualName(FunctionObservation.FUNCTION_OBSERVATION.getContextualName())
			.tagsProvider(this.tagsProvider)
			.observe(() -> {
				Object r = message == null ? targetFunction.get() : targetFunction.apply(invocationMessage);
				context.setOutput(r);
				return r;
			});
		if (result == null) {
			if (log.isDebugEnabled()) {
				log.debug("Returned message is null - we have a consumer");
			}
			return null;
		}
		return context.getModifiedOutput();
	}

	@Override
	public void setTagsProvider(FunctionTagsProvider functionTagsProvider) {
		this.tagsProvider = functionTagsProvider;
	}
}
