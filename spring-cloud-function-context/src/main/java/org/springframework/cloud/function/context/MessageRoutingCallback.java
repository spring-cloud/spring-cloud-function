/*
 * Copyright 2021-2021 the original author or authors.
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
 * Once implementation is registered as a bean in application context
 * it will be picked up by a {@link RoutingFunction} and used to determine the name of the
 * route-to function definition.
 *
 * While {@link RoutingFunction} provides several mechanisms to determine the route-to function definition
 * this callback takes precedence over all of them.
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public interface MessageRoutingCallback {

	/**
	 * Determines the name of the function definition to route incoming {@link Message}.
	 *
	 * @param message instance of incoming {@link Message}
	 * @return the name of the route-to function definition
	 */
	String functionDefinition(Message<?> message);
}
