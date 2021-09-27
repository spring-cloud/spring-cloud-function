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


import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.grpc.MessagingServiceGrpc.MessagingServiceImplBase;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 *
 */
class GrpcServerMessageHandler extends MessagingServiceImplBase implements SmartLifecycle {

	private Log logger = LogFactory.getLog(GrpcServerMessageHandler.class);

	private final ExecutorService executor;

	private final FunctionProperties funcProperties;

	private final FunctionCatalog functionCatalog;

	private boolean running;


	GrpcServerMessageHandler(FunctionProperties funcProperties, FunctionCatalog functionCatalog) {
		this.functionCatalog = functionCatalog;
		this.funcProperties = funcProperties;
		this.executor = Executors.newCachedThreadPool();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void requestReply(GrpcMessage request, StreamObserver<GrpcMessage> responseObserver) {
		Message<byte[]> message = GrpcUtils.fromGrpcMessage(request);
		FunctionInvocationWrapper function = this.resolveFunction(message.getHeaders());

		Message<byte[]> replyMessage = (Message<byte[]>) function.apply(message);

		GrpcMessage reply = GrpcUtils.toGrpcMessage(replyMessage);

		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void serverStream(GrpcMessage request, StreamObserver<GrpcMessage> responseObserver) {
		Message<byte[]> message = GrpcUtils.fromGrpcMessage(request);
		FunctionInvocationWrapper function = this.resolveFunction(message.getHeaders());
		Publisher<Message<byte[]>> replyStream = (Publisher<Message<byte[]>>) function.apply(message);
		Flux.from(replyStream).doOnNext(replyMessage -> {
			responseObserver.onNext(GrpcUtils.toGrpcMessage(replyMessage));
		})
		.doOnComplete(() -> responseObserver.onCompleted())
		.subscribe();
	}


	@SuppressWarnings("unchecked")
	@Override
	public StreamObserver<GrpcMessage> clientStream(StreamObserver<GrpcMessage> responseObserver) {
		ServerCallStreamObserver<GrpcMessage> serverCallStreamObserver = (ServerCallStreamObserver<GrpcMessage>) responseObserver;
		serverCallStreamObserver.disableAutoInboundFlowControl();

		FunctionInvocationWrapper function = this.resolveFunction(null);

		AtomicBoolean wasReady = new AtomicBoolean(false);
		serverCallStreamObserver.setOnReadyHandler(() -> {
			if (serverCallStreamObserver.isReady() && !wasReady.get()) {
				wasReady.set(true);
				logger.info("gRPC Server receiving stream is ready.");
				serverCallStreamObserver.request(1);
			}
		});

		if (!function.isInputTypePublisher()) {
			throw new UnsupportedOperationException("The client streaming is "
					+ "not supported for functions that accept non-Publisher: "
					+ function);
		}
		else if (function.isOutputTypePublisher()) {
			throw new UnsupportedOperationException("The client streaming is "
					+ "not supported for functions that return Publisher: "
					+ function);
		}
		else {
			Many<Message<byte[]>> inputStream = Sinks.many().unicast().onBackpressureBuffer();
			Flux<Message<byte[]>> inputStreamFlux = inputStream.asFlux();

			LinkedBlockingQueue<Message<byte[]>> resultRef = new LinkedBlockingQueue<>(1);
			this.executor.execute(() -> {
				Message<byte[]> replyMessage = (Message<byte[]>) function.apply(inputStreamFlux);
				if (logger.isDebugEnabled()) {
					logger.debug("Function invocation reply: " + replyMessage);
				}
				resultRef.offer(replyMessage);
			});

			return new StreamObserver<GrpcMessage>() {

				@Override
				public void onNext(GrpcMessage inputMessage) {
					if (logger.isDebugEnabled()) {
						logger.debug("gRPC Server receiving: " + inputMessage);
					}

					inputStream.tryEmitNext(GrpcUtils.fromGrpcMessage(inputMessage));
					serverCallStreamObserver.request(1);
				}

				@Override
				public void onError(Throwable t) {
					t.printStackTrace();
					responseObserver.onCompleted();
				}

				@Override
				public void onCompleted() {
					logger.info("gRPC Server has finished receiving data.");
					inputStream.tryEmitComplete();
					try {
						responseObserver.onNext(GrpcUtils.toGrpcMessage(resultRef.poll(Integer.MAX_VALUE, TimeUnit.MILLISECONDS)));
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					responseObserver.onCompleted();
				}
			};
		}
	}

	@Override
	public StreamObserver<GrpcMessage> biStream(StreamObserver<GrpcMessage> responseObserver) {
		ServerCallStreamObserver<GrpcMessage> serverCallStreamObserver = (ServerCallStreamObserver<GrpcMessage>) responseObserver;
		serverCallStreamObserver.disableAutoInboundFlowControl();

		FunctionInvocationWrapper function = this.resolveFunction(null);

		AtomicBoolean wasReady = new AtomicBoolean(false);
		serverCallStreamObserver.setOnReadyHandler(() -> {
			if (serverCallStreamObserver.isReady() && !wasReady.get()) {
				wasReady.set(true);
				logger.info("gRPC Server receiving stream is ready.");
				serverCallStreamObserver.request(1);
			}
		});

		if (function.isInputTypePublisher()) {
			if (function.isOutputTypePublisher()) {
				return this.biStreamReactive(responseObserver, serverCallStreamObserver);
			}
			throw new UnsupportedOperationException("The bi-directional streaming is "
					+ "not supported for functions that accept Publisher but return non-Publisher: "
					+ function);
		}
		else {
			if (!function.isOutputTypePublisher()) {
				return this.biStreamImperative(responseObserver, serverCallStreamObserver, wasReady);
			}
			throw new UnsupportedOperationException("The bidirection streaming is "
					+ "not supported for functions that accept non-Publisher but return Publisher: "
					+ function);

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
					FunctionInvocationWrapper function = resolveFunction(message.getHeaders());

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
				logger.info("gRPC Server has finished receiving data.");
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public void start() {
		this.running = true;
	}

	@Override
	public void stop() {
		this.executor.shutdown();
		try {
			Assert.isTrue(this.executor.awaitTermination(5000, TimeUnit.MILLISECONDS), "gRPC Server executor timed out while stopping, "
					+ "since there are currently executing tasks");
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		this.running = false;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	@SuppressWarnings("unchecked")
	private StreamObserver<GrpcMessage> biStreamReactive(StreamObserver<GrpcMessage> responseObserver,
			ServerCallStreamObserver<GrpcMessage> serverCallStreamObserver) {
		Many<Message<byte[]>> inputStream = Sinks.many().unicast().onBackpressureBuffer();
		Flux<Message<byte[]>> inputStreamFlux = inputStream.asFlux();

		FunctionInvocationWrapper function = this.resolveFunction(null);

		Publisher<Message<byte[]>> outputPublisher = (Publisher<Message<byte[]>>) function.apply(inputStreamFlux);

		Flux.from(outputPublisher).subscribe(functionResult -> {
			GrpcMessage outputMessage = GrpcUtils.toGrpcMessage(functionResult);
			if (logger.isDebugEnabled()) {
				logger.debug("gRPC Server replying: " + outputMessage);
			}
			responseObserver.onNext(outputMessage);
		});

		return new StreamObserver<GrpcMessage>() {

			@Override
			public void onNext(GrpcMessage inputMessage) {
				if (logger.isDebugEnabled()) {
					logger.debug("gRPC Server receiving: " + inputMessage);
				}

				inputStream.tryEmitNext(GrpcUtils.fromGrpcMessage(inputMessage));
				serverCallStreamObserver.request(1);
			}

			@Override
			public void onError(Throwable t) {
				t.printStackTrace();
				responseObserver.onCompleted();
			}

			@Override
			public void onCompleted() {
				logger.info("gRPC Server has finished receiving data.");
				inputStream.tryEmitComplete();
				responseObserver.onCompleted();
			}
		};
	}

	private FunctionInvocationWrapper resolveFunction(Map<String, Object> headers) {
		String functionDefinition = funcProperties.getDefinition();
		if (!CollectionUtils.isEmpty(headers) && headers.containsKey(FunctionProperties.FUNCTION_DEFINITION)) {
			functionDefinition = (String) headers.get(FunctionProperties.FUNCTION_DEFINITION);
		}
		FunctionInvocationWrapper function = this.functionCatalog.lookup(functionDefinition, "application/json");
		Assert.notNull(function, "Failed to lookup function " + funcProperties.getDefinition());
		return function;
	}
}


