/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.cloud.function.context.catalog;

import org.reactivestreams.Publisher;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

/**
 * Wrapper that acts as around advise over function invocation. If registered as bean it
 * will be autowired into {@link FunctionInvocationWrapper}. Keep in mind that it only
 * affects imperative invocations where input is {@link Message}
 *
 * NOTE: This API is experimental and and could change without notice. It is intended for
 * internal use only (e.g., spring-cloud-sleuth)
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public abstract class FunctionAroundWrapper {

	public final Object apply(Object input, FunctionInvocationWrapper targetFunction) {

		String functionalTracingEnabledStr = System.getProperty("spring.cloud.function.observability.enabled");
		boolean functionalTracingEnabled = !StringUtils.hasText(functionalTracingEnabledStr)
				|| Boolean.parseBoolean(functionalTracingEnabledStr);
		if (functionalTracingEnabled && !(input instanceof Publisher) && input instanceof Message
				&& !FunctionTypeUtils.isCollectionOfMessage(targetFunction.getOutputType())) {
			Object result = this.doApply(input, targetFunction);
			targetFunction.wrapped = false;
			return result;
		}
		else {
			return targetFunction.apply(input);
		}
	}

	protected abstract Object doApply(Object input, FunctionInvocationWrapper targetFunction);

}
