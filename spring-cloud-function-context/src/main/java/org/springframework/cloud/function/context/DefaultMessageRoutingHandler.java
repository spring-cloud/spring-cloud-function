/*
 * Copyright 2016-present the original author or authors.
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

import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.messaging.Message;

/**
 * Strategy for implementing a handler for un-routable messages.
 * Works in parallel with {@link RoutingFunction}. When registered as a bean, RoutingFunction will not throw
 * an exception if it can not route message and instead such message will be routed to this function.
 * Its default implementation simply logs the un-routable event.
 * Users are encouraged to provide their own implementation of this class.
 *
 * @author Oleg Zhurakousky
 * @since 3.2.9
 *
 */
public class DefaultMessageRoutingHandler implements Consumer<Message<?>> {

	Log logger = LogFactory.getLog(DefaultMessageRoutingHandler.class);

	@Override
	public void accept(Message<?> message) {
		if (logger.isDebugEnabled()) {
			logger.debug("Route-to function can not be located in FunctionCatalog. Dropping unroutable message: " + message + "");
		}
		else {
			logger.warn("Route-to function can not be located in FunctionCatalog. Droping message");
		}
	}
}
