/*
 * Copyright 2013-2021 the original author or authors.
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

import io.micrometer.common.KeyValues;

/**
 * Default implementation of {@link FunctionReceiverObservationConvention}.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class DefaultFunctionReceiverObservationConvention implements FunctionReceiverObservationConvention {

	/**
	 * Singleton instance of this convention.
	 */
	public static final FunctionReceiverObservationConvention INSTANCE = new DefaultFunctionReceiverObservationConvention();

	@Override
	public KeyValues getLowCardinalityKeyValues(FunctionReceiverContext context) {
		return KeyValues.of(FunctionObservation.FunctionLowCardinalityTags.FUNCTION_NAME.withValue(context.getTargetFunction().getFunctionDefinition()));
	}

	@Override
	public String getName() {
		return "spring.cloud.function.receive";
	}

	@Override
	public String getContextualName(FunctionReceiverContext context) {
		return context.getTargetFunction().getFunctionDefinition() + " receive";
	}
}
