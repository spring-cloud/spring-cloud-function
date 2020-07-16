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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.function.Function;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.util.retry.Retry;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;



/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 *
 */
class RSocketForwardingFunction implements Function<Message<byte[]>, Publisher<Message<byte[]>>> {
	private static Log logger = LogFactory.getLog(RSocketForwardingFunction.class);

	private final RSocket rSocket;

	private final FunctionInvocationWrapper targetFunction;

	RSocketForwardingFunction(FunctionInvocationWrapper targetFunction, InetSocketAddress outputAddress) {
		this.targetFunction = targetFunction;
		this.rSocket = outputAddress == null ? null
				: RSocketConnector.connectWith(TcpClientTransport.create(outputAddress))
					.log()
					.retryWhen(Retry.backoff(5, Duration.ofSeconds(1)))
					.block();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Publisher<Message<byte[]>> apply(Message<byte[]> input) {
		if (logger.isDebugEnabled()) {
			logger.debug("Executiing: " + this.targetFunction);
		}

		Object rawResult = this.targetFunction.apply(input);
		Publisher<Message<byte[]>> resultMessage = this.rSocket
				.requestStream(DefaultPayload.create(((Message<byte[]>) rawResult).getPayload()))
				.map(this::buildResultMessage);
		return resultMessage;
	}

	private Message<byte[]> buildResultMessage(Payload payload) {
		ByteBuffer payloadBuffer = payload.getData();
		byte[] payloadData = new byte[payloadBuffer.remaining()];
		payloadBuffer.get(payloadData);
		return MessageBuilder.withPayload(payloadData).build();
	}
}
