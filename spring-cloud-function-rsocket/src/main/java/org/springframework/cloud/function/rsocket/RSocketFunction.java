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

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.function.Function;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Wrapper over an instance of target Function (represented by {@link FunctionInvocationWrapper})
 * which will use the result of the invocation of such function as an input to another RSocket
 * effectively composing two functions over RSocket.
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
class RSocketFunction implements Function<Message<byte[]>, Mono<Message<byte[]>>> {

	private final String bindAddress;

	private final int port;

	private final FunctionInvocationWrapper function;

	private final RSocket rSocket;

	RSocketFunction(String bindAddress, int port, FunctionInvocationWrapper function) {
		this.bindAddress = bindAddress;
		this.port = port;
		this.function = function;
		this.rSocket = RSocketConnector.connectWith(TcpClientTransport.create(this.bindAddress, this.port))
				.log()
				.retryWhen(Retry.backoff(5, Duration.ofSeconds(1)))
				.block();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Message<byte[]>> apply(Message<byte[]> input) {
		Message<byte[]> result = (Message<byte[]>) function.apply(input);
		Mono<Message<byte[]>> resultMessage = null;
		if (result != null) {
			resultMessage = this.rSocket
				.requestResponse(DefaultPayload.create(result.getPayload()))
				.map(this::buildResultMessage);
		}
		return resultMessage;
	}

	private Message<byte[]> buildResultMessage(Payload payload) {
		ByteBuffer payloadBuffer = payload.getData();
		byte[] payloadData = new byte[payloadBuffer.remaining()];
		payloadBuffer.get(payloadData);

//		ByteBuffer headersBuffer = responsePayload.getMetadata();
//		byte[] rawData = new byte[payloadBuffer.remaining()];
//		payloadBuffer.get(rawData);
		return MessageBuilder.withPayload(payloadData).build();
	}
}
