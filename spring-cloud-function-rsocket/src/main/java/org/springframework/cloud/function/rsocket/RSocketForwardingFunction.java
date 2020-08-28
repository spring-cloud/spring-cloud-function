/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.function.rsocket;

import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.support.GenericMessage;


/**
 * Wrapper over an instance of target Function (represented by {@link FunctionInvocationWrapper})
 * which will use the result of the invocation of such function as an input to another RSocket
 * effectively composing two functions over RSocket.
 * <p>
 * Note: the remote RSocket route is not required to represent Spring Cloud Function binding.
 *
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 *
 * @since 3.1
 *
 */
class RSocketForwardingFunction implements Function<Message<byte[]>, Publisher<Message<byte[]>>> {

	private static final Log LOGGER = LogFactory.getLog(RSocketForwardingFunction.class);

	private final FunctionInvocationWrapper targetFunction;

	private final RSocketRequester rSocketRequester;

	RSocketForwardingFunction(FunctionInvocationWrapper targetFunction, RSocketRequester rsocketRequester,
		String remoteFunctionName) {

		this.targetFunction = targetFunction;
		this.rSocketRequester = rsocketRequester;
	}

	@Override
	public Publisher<Message<byte[]>> apply(Message<byte[]> input) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Executing: " + this.targetFunction);
		}

		Mono<Object> targetFunctionCall = Mono.just(input)
			.map(this.targetFunction)
			.cast(Message.class)
			.map(Message::getPayload);

		return this.rSocketRequester
			.route("")
			.data(targetFunctionCall, byte[].class)
			.retrieveFlux(byte[].class)
			.map(GenericMessage::new);
	}
}
