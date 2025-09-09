/*
 * Copyright 2021-present the original author or authors.
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

package org.springframework.cloud.function.context;

import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.messaging.Message;

/**
 * Java-based strategy to assist with determining the name of the route-to function definition.
 * Once an implementation is registered as a bean in application context
 * it will be picked up by the {@link RoutingFunction}.
 * <p/>
 * While {@link RoutingFunction} provides several mechanisms to determine the route-to function definition
 * this callback takes precedence over all of them.
 *
 * @author Oleg Zhurakousky
 * @author John Blum
 * @since 3.1
 */
public interface MessageRoutingCallback {

	/**
	 * Computes and returns an instance of {@link String}, which encapsulates,
	 * at the very minimum, a function definition.
	 * <p/>
	 * Providing such message is primarily an optimization feature. It could be useful for cases
	 * where routing procedure is complex and results in, let's say, conversion of the payload to
	 * the target type, which would effectively be thrown away if the ability to modify the target
	 * message for downstream use didn't exist, resulting in repeated transformation, type conversion etc.
	 *
	 * @param message input message
	 * @return instance of {@link String} containing the result of the routing computation
	 */
	default String routingResult(Message<?> message) {
		return (String) message.getHeaders().get(FunctionProperties.FUNCTION_DEFINITION);
	}
}
