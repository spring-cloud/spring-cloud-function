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

import java.lang.reflect.Type;
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
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.lang.Nullable;
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
class RSocketFunction implements Function<Message<byte[]>, Publisher<Message<byte[]>>> {

	private static String splash = "   ____         _             _______             __  ____              __  _             ___  ____         __       __ \n" +
			"  / __/__  ____(_)__  ___ _  / ___/ /__  __ _____/ / / __/_ _____  ____/ /_(_)__  ___    / _ \\/ __/__  ____/ /_____ / /_\n" +
			" _\\ \\/ _ \\/ __/ / _ \\/ _ `/ / /__/ / _ \\/ // / _  / / _// // / _ \\/ __/ __/ / _ \\/ _ \\  / , _/\\ \\/ _ \\/ __/  '_/ -_) __/\n" +
			"/___/ .__/_/ /_/_//_/\\_, /  \\___/_/\\___/\\_,_/\\_,_/ /_/  \\_,_/_//_/\\__/\\__/_/\\___/_//_/ /_/|_/___/\\___/\\__/_/\\_\\\\__/\\__/ \n" +
			"   /_/              /___/                                                                                               \n" +
			"";

	private static Log logger = LogFactory.getLog(RSocketFunction.class);

	private final InetSocketAddress listenAddress;

	private final InetSocketAddress outputAddress;

	private final FunctionInvocationWrapper targetFunction;

	private final RSocket rSocket;

	private Disposable rsocketConnection;

	RSocketFunction(FunctionInvocationWrapper targetFunction, InetSocketAddress listenAddress, @Nullable InetSocketAddress outputAddress) {
		this.listenAddress = listenAddress;
		this.outputAddress = outputAddress;
		this.targetFunction = targetFunction;
		this.rSocket = outputAddress == null ? null
						: RSocketConnector.connectWith(TcpClientTransport.create(this.outputAddress))
							.log()
							.retryWhen(Retry.backoff(5, Duration.ofSeconds(1)))
							.block();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Publisher<Message<byte[]>> apply(Message<byte[]> input) {
		if (logger.isDebugEnabled()) {
			logger.debug("Executiing: " + this.targetFunction + " on " + this.listenAddress);
		}

		Object rawResult = this.targetFunction.apply(input);
		if (rawResult instanceof Message) {
			Publisher<Message<byte[]>> resultMessage = null;
			if (this.outputAddress != null) {
				resultMessage = this.rSocket
						.requestStream(DefaultPayload.create(((Message<byte[]>) rawResult).getPayload()))
						.map(this::buildResultMessage);
			}
			resultMessage = rawResult instanceof Publisher ? (Publisher<Message<byte[]>>) rawResult : Mono.just((Message<byte[]>) rawResult);
			return resultMessage;
		}
		else  {
			return (Publisher<Message<byte[]>>) rawResult;
		}

	}

	void start() {
		Type functionType = this.targetFunction.getFunctionType();

		RSocket rsocket = buildRSocket(this.targetFunction.getFunctionDefinition(), functionType, this);
		if (this.listenAddress != null) {
			this.rsocketConnection = RSocketConnectionUtils.createServerSocket(rsocket, this.listenAddress);
			this.printSplashScreen(this.targetFunction.getFunctionDefinition(), functionType);
		}
	}

	void stop() {
		if (this.rsocketConnection != null) {
			this.rsocketConnection.dispose();
		}
	}

	private RSocket buildRSocket(String definition, Type functionType, Function<Message<byte[]>, Publisher<Message<byte[]>>> function) {
		RSocket clientRSocket = new RSocket() { // imperative function or Function<?, Mono> = requestResponse
			@Override
			public Mono<Payload> requestResponse(Payload payload) {
				if (logger.isDebugEnabled()) {
					logger.debug("Invoking function '" + definition + "' as RSocket `requestResponse`.");
				}

				if (isFunctionReactive(functionType)) {
					Flux<Payload> result = this.requestChannel(Flux.just(payload));
					return Mono.from(result);
				}
				else {
					Message<byte[]> inputMessage = deserealizePayload(payload);
					Mono<Message<byte[]>> result = Mono.from(function.apply(inputMessage));
					if (rSocket != null) {
						return result.flatMap(message -> {
							Mono<Payload> requestResponse = rSocket.requestResponse(DefaultPayload.create(message.getPayload()));
							return requestResponse;
						});
					}
					else {
						return result.map(message -> DefaultPayload.create(message.getPayload()));
					}
				}
			}

			@Override
			public Flux<Payload> requestStream(Payload payload) {
				if (logger.isDebugEnabled()) {
					logger.debug("Invoking function '" + definition + "' as RSocket `requestStream`.");
				}
				if (isFunctionReactive(functionType)) {
					return this.requestChannel(Flux.just(payload));
				}
				else {
					Message<byte[]> inputMessage = deserealizePayload(payload);
					Flux<Message<byte[]>> result = Flux.from(function.apply(inputMessage));
					return result.map(message -> DefaultPayload.create(message.getPayload()));
				}
			}

			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
				if (logger.isDebugEnabled()) {
					logger.debug("Invoking function '" + definition + "' as RSocket `requestChannel`.");
				}
				if (isFunctionReactive(functionType)) {
					return Flux.from(payloads)
							.transform(inputFlux -> inputFlux.map(payload -> deserealizePayload(payload)))
							.transform((Function) targetFunction)
							.transform(outputFlux -> ((Flux<Message<byte[]>>) outputFlux).map(message -> DefaultPayload.create(message.getPayload())));
				}
				else {
					return Flux.from(payloads)
							.transform(flux -> {
								return flux.flatMap(payload -> {
									Message<byte[]> inputMessage = deserealizePayload(payload);
									Flux<Message<byte[]>> result = Flux.from(function.apply(inputMessage));
									return result;
								});
							})
							.doOnNext(System.out::println)
							.transform(outputFlux -> outputFlux.map(message -> DefaultPayload.create(message.getPayload())));
				}

			}
		};
		return clientRSocket;
	}

	private static boolean isFunctionReactive(Type functionType) {
		Type inputType = FunctionTypeUtils.getInputType(functionType, 0);
		Type outputType = FunctionTypeUtils.getOutputType(functionType, 0);
		return FunctionTypeUtils.isPublisher(inputType) && FunctionTypeUtils.isFlux(outputType);
	}

	@SuppressWarnings("rawtypes")
	private static Message<byte[]> deserealizePayload(Payload payload) {
		ByteBuffer buffer = payload.getData();
		byte[] rawData = new byte[buffer.remaining()];
		buffer.get(rawData);
		if (payload.hasMetadata()) {
			String metadata = payload.getMetadataUtf8(); // TODO see what to do with it
		}
		MessageBuilder builder = MessageBuilder.withPayload(rawData);
		Message<byte[]> inputMessage = builder.build();
		return inputMessage;

	}

	private Message<byte[]> buildResultMessage(Payload payload) {
		ByteBuffer payloadBuffer = payload.getData();
		byte[] payloadData = new byte[payloadBuffer.remaining()];
		payloadBuffer.get(payloadData);
		return MessageBuilder.withPayload(payloadData).build();
	}

	private void printSplashScreen(String definition, Type type) {
		System.out.println(splash);
		System.out.println("Function Definition: " + definition + ":[" + type + "]");
		System.out.println("RSocket Listen Address: " + this.listenAddress);
		System.out.println("RSocket Target Address: " + this.outputAddress);
		System.out.println("======================================================\n");
	}

}
