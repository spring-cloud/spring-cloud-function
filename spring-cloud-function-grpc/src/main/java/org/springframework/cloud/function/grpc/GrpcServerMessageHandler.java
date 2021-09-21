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


import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.grpc.MessagingServiceGrpc.MessagingServiceImplBase;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 *
 */
class GrpcServerMessageHandler extends MessagingServiceImplBase {

	private Log logger = LogFactory.getLog(GrpcServerMessageHandler.class);

	private final FunctionInvocationWrapper function;

	GrpcServerMessageHandler(FunctionProperties funcProperties, FunctionCatalog functionCatalog) {
		this.function = functionCatalog.lookup(funcProperties.getDefinition(), "application/json");
		Assert.notNull(this.function, "Failed to lookup function " + funcProperties.getDefinition());
	}

	@Override
	@SuppressWarnings("unchecked")
	public void requestReply(GrpcMessage request, StreamObserver<GrpcMessage> responseObserver) {
		Message<byte[]> message = GrpcUtils.fromGrpcMessage(request);

		Message<byte[]> replyMessage = (Message<byte[]>) this.function.apply(message);

		GrpcMessage reply = GrpcUtils.toGrpcMessage(replyMessage);

		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}
//
//	@Override
//	public void serverStream(GrpcMessage request,
//			StreamObserver<GrpcMessage> responseObserver) {
//
//	}
//
//	@Override
//	public StreamObserver<GrpcMessage> clientStream(
//			StreamObserver<GrpcMessage> responseObserver) {
//		return null;
//	}
//
	@Override
	public StreamObserver<GrpcMessage> biStream(StreamObserver<GrpcMessage> responseObserver) {
		ServerCallStreamObserver<GrpcMessage> serverCallStreamObserver = (ServerCallStreamObserver<GrpcMessage>) responseObserver;
		serverCallStreamObserver.disableAutoInboundFlowControl();

		AtomicBoolean wasReady = new AtomicBoolean(false);
		serverCallStreamObserver.setOnReadyHandler(() -> {
			if (serverCallStreamObserver.isReady() && !wasReady.get()) {
				wasReady.set(true);
				logger.info("Server stream is ready");
				serverCallStreamObserver.request(1);
			}
		});

		if (function.isInputTypePublisher()) {
			return this.biStreamReactive(responseObserver, serverCallStreamObserver);
		}
		else {
			return this.biStreamImperative(responseObserver, serverCallStreamObserver, wasReady);
		}
	}

	private StreamObserver<GrpcMessage> biStreamImperative(StreamObserver<GrpcMessage> responseObserver,
			ServerCallStreamObserver<GrpcMessage> serverCallStreamObserver, AtomicBoolean wasReady) {
		return new StreamObserver<GrpcMessage>() {

			@SuppressWarnings("unchecked")
			@Override
			public void onNext(GrpcMessage request) {
				try {
					Message<byte[]> message = GrpcUtils.fromGrpcMessage(request);

					Message<byte[]> replyMessage = (Message<byte[]>) function.apply(message);

					GrpcMessage reply = GrpcUtils.toGrpcMessage(replyMessage);

					responseObserver.onNext(reply);

					// Check the provided ServerCallStreamObserver to see if it is still
					// ready to accept more messages.
					if (serverCallStreamObserver.isReady()) {
						serverCallStreamObserver.request(1);
					}
					else {
						wasReady.set(false);
					}
				}
				catch (Throwable throwable) {
					throwable.printStackTrace();
					responseObserver.onError(
							Status.UNKNOWN.withDescription("Error handling request").withCause(throwable).asException());
				}
			}

			@Override
			public void onError(Throwable t) {
				t.printStackTrace();
				responseObserver.onCompleted();
			}

			@Override
			public void onCompleted() {
				logger.info("Server Stream is complete");
				responseObserver.onCompleted();
			}
		};
	}

	@SuppressWarnings("unchecked")
	private StreamObserver<GrpcMessage> biStreamReactive(StreamObserver<GrpcMessage> responseObserver,
			ServerCallStreamObserver<GrpcMessage> serverCallStreamObserver) {
		Many<Message<byte[]>> sink = Sinks.many().unicast().onBackpressureBuffer();
		Flux<Message<byte[]>> flux = sink.asFlux();

		Flux<Message<byte[]>> connectedFlux = (Flux<Message<byte[]>>) function.apply(flux);

		connectedFlux.subscribe(functionResult -> {
			GrpcMessage reply = GrpcUtils.toGrpcMessage(functionResult);
			responseObserver.onNext(reply);
		});

		return new StreamObserver<GrpcMessage>() {

			@Override
			public void onNext(GrpcMessage value) {
				sink.tryEmitNext(GrpcUtils.fromGrpcMessage(value));
				serverCallStreamObserver.request(1);
			}

			@Override
			public void onError(Throwable t) {
				t.printStackTrace();
				responseObserver.onCompleted();
			}

			@Override
			public void onCompleted() {
				logger.info("Server stream is complete");
				sink.tryEmitComplete();
				responseObserver.onCompleted();
			}
		};
	}
}
