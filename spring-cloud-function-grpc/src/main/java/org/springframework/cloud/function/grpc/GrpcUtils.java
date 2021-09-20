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

package org.springframework.cloud.function.grpc;

import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 *
 */
public final class GrpcUtils {

	private static Log logger = LogFactory.getLog(GrpcUtils.class);

	private GrpcUtils() {

	}

	public static GrpcMessage toGrpcMessage(byte[] payload, Map<String, String> headers) {
		return GrpcMessage.newBuilder()
				.setPayload(ByteString.copyFrom(payload))
				.putAllHeaders(headers)
				.build();
	}

	public static GrpcMessage toGrpcMessage(Message<byte[]> message) {
		Map<String, String> stringHeaders = new HashMap<>();
		message.getHeaders().forEach((k, v) -> {
			stringHeaders.put(k, v.toString());
		});
		return toGrpcMessage(message.getPayload(), stringHeaders);
	}

	public static Message<byte[]> fromGrpcMessage(GrpcMessage message) {
		return MessageBuilder.withPayload(message.getPayload().toByteArray())
				.copyHeaders(message.getHeadersMap())
				.build();
	}

	public static Message<byte[]> requestReply(Message<byte[]> inputMessage) {
		return requestReply("localhost", FunctionGrpcProperties.GRPC_PORT, inputMessage);
	}

	public static Message<byte[]> requestReply(String host, int port, Message<byte[]> inputMessage) {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext().build();
		MessagingServiceGrpc.MessagingServiceBlockingStub stub = MessagingServiceGrpc
				.newBlockingStub(channel);

		GrpcMessage response = stub.requestReply(toGrpcMessage(inputMessage));
		channel.shutdown();
		return fromGrpcMessage(response);
	}

	/**
	 * Utility method to support bi-directional streaming interaction. Will connect to gRPC server using default host/port,
	 * otherwise use {@link #biStreaming(String, int, Flux)} method.
	 *
	 * Keep in mind that there is no implied relationship between input stream and output stream.
	 * They are completely independent where one may end before the other.
	 *
	 * @param inputStream {@code FluxMessage<byte[]>>} representing input stream.
	 * @return {@code FluxMessage<byte[]>>} representing output stream
	 */
	public static Flux<Message<byte[]>> biStreaming(Flux<Message<byte[]>> inputStream) {
		return biStreaming("localhost", FunctionGrpcProperties.GRPC_PORT, inputStream);
	}

	/**
	 * Utility method to support bi-directional streaming interaction.
	 * Keep in mind that there is no implied relationship between input stream and output stream.
	 * They are completely independent where one may end before the other.
	 *
	 * @param host gRPC server host name
	 * @param port gRPC server port
	 * @param inputStream {@code FluxMessage<byte[]>>} representing input stream
	 * @return {@code FluxMessage<byte[]>>} representing output stream
	 */
	public static Flux<Message<byte[]>> biStreaming(String host, int port, Flux<Message<byte[]>> inputStream) {
		ManagedChannel channel = ManagedChannelBuilder
				.forAddress(host, port)
				.usePlaintext().build();
		MessagingServiceGrpc.MessagingServiceStub stub = MessagingServiceGrpc
				.newStub(channel);
		Many<Message<byte[]>> sink = Sinks.many().unicast().onBackpressureBuffer();

		ClientResponseObserver<GrpcMessage, GrpcMessage> clientResponseObserver = clientResponseObserver(inputStream, sink);

		stub.biStream(clientResponseObserver);

		return sink.asFlux().doOnComplete(() -> {
			logger.debug("Shutting down channel");
			channel.shutdown();
		});
	}

	private static ClientResponseObserver<GrpcMessage, GrpcMessage> clientResponseObserver(Flux<Message<byte[]>> inputStream, Many<Message<byte[]>> sink) {
		return new ClientResponseObserver<GrpcMessage, GrpcMessage>() {

			ClientCallStreamObserver<GrpcMessage> requestStreamObserver;

			@Override
			public void beforeStart(ClientCallStreamObserver<GrpcMessage> requestStreamObserver) {
				this.requestStreamObserver = requestStreamObserver;
				requestStreamObserver.disableAutoInboundFlowControl();

				requestStreamObserver.setOnReadyHandler(new Runnable() {
					@Override
					public void run() {
						inputStream
						.doOnNext(request -> {
							if (logger.isDebugEnabled()) {
								logger.debug("Sending message: " + request);
							}
							requestStreamObserver.onNext(GrpcUtils.toGrpcMessage(request));
						})
						.doOnComplete(() -> {
							requestStreamObserver.onCompleted();
						})
						.subscribe();
					}
				});
			}

			@Override
			public void onNext(GrpcMessage message) {
				if (logger.isDebugEnabled()) {
					logger.debug("Receiving message: " + message);
				}
				sink.tryEmitNext(fromGrpcMessage(message));
				requestStreamObserver.request(1);

			}

			@Override
			public void onError(Throwable t) {
				t.printStackTrace();
			}

			@Override
			public void onCompleted() {
				logger.info("Client stream is complete");
				sink.tryEmitComplete(); // TODO revisit as this would complete the server stream simply because the client is done.
										// Perhaps we need to expose some boolean value when this is desirable
			}
		};
	}
}
