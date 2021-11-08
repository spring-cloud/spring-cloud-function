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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.protobuf.GeneratedMessageV3;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 */
public class MessageHandlingHelper<T extends GeneratedMessageV3> implements SmartLifecycle {

	private Log logger = LogFactory.getLog(MessageHandlingHelper.class);

	private final List<GrpcMessageConverter<?>> grpcConverters;

	private final FunctionProperties funcProperties;

	private final FunctionCatalog functionCatalog;

	private final ExecutorService executor;

	private boolean running;

	public MessageHandlingHelper(List<GrpcMessageConverter<?>> grpcConverters,
			FunctionCatalog functionCatalog, FunctionProperties funcProperties) {
		this.grpcConverters = grpcConverters;
		this.funcProperties = funcProperties;
		this.functionCatalog = functionCatalog;
		this.executor = Executors.newCachedThreadPool();
	}

	@SuppressWarnings("unchecked")
	public void requestReply(T request, StreamObserver<T> responseObserver) {
		Message<byte[]> message = this.toSpringMessage(request);
		FunctionInvocationWrapper function = this.resolveFunction(message.getHeaders());

		Message<byte[]> replyMessage = (Message<byte[]>) function.apply(message);
		GeneratedMessageV3 reply = this.toGrpcMessage(replyMessage, (Class<T>) request.getClass());

		responseObserver.onNext((T) reply);
		responseObserver.onCompleted();
	}

	@SuppressWarnings("unchecked")
	public void serverStream(T request, StreamObserver<T> responseObserver) {
		Message<byte[]> message = this.toSpringMessage(request);
		FunctionInvocationWrapper function = this.resolveFunction(message.getHeaders());
		Publisher<Message<byte[]>> replyStream = (Publisher<Message<byte[]>>) function.apply(message);
		Flux.from(replyStream).doOnNext(replyMessage -> {
			responseObserver.onNext(this.toGrpcMessage(replyMessage, (Class<T>) request.getClass()));
		})
		.doOnComplete(() -> responseObserver.onCompleted())
		.subscribe();
	}

	@SuppressWarnings("unchecked")
	public StreamObserver<T> clientStream(StreamObserver<T> responseObserver, Class<T> grpcMessageType) {
		ServerCallStreamObserver<T> serverCallStreamObserver = (ServerCallStreamObserver<T>) responseObserver;
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

			return new StreamObserver<T>() {
				@Override
				public void onNext(T inputMessage) {
					if (logger.isDebugEnabled()) {
						logger.debug("gRPC Server receiving: " + inputMessage);
					}
					inputStream.tryEmitNext(toSpringMessage(inputMessage));
					serverCallStreamObserver.request(1);
				}

				@Override
				public void onError(Throwable t) {
					t.printStackTrace();
					responseObserver.onError(Status.UNKNOWN.withDescription("Error handling request")
							.withCause(t).asRuntimeException());
				}

				@Override
				public void onCompleted() {
					logger.info("gRPC Server has finished receiving data.");
					inputStream.tryEmitComplete();
					try {
						responseObserver.onNext(toGrpcMessage(resultRef.poll(Integer.MAX_VALUE, TimeUnit.MILLISECONDS), grpcMessageType));
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					finally {
						responseObserver.onCompleted();
					}
				}
			};
		}
	}

	public StreamObserver<T> biStream(StreamObserver<T> responseObserver, Class<T> grpcMessageType) {
		ServerCallStreamObserver<T> serverCallStreamObserver = (ServerCallStreamObserver<T>) responseObserver;
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
				return this.biStreamReactive(responseObserver, serverCallStreamObserver, grpcMessageType);
			}
			UnsupportedOperationException ex = new UnsupportedOperationException("The bi-directional streaming is "
					+ "not supported for functions that accept Publisher but return non-Publisher: "
					+ function);
			responseObserver.onCompleted();
			throw ex;
		}
		else {
			if (!function.isOutputTypePublisher()) {
				return this.biStreamImperative(responseObserver, serverCallStreamObserver, wasReady);
			}

			UnsupportedOperationException ex = new UnsupportedOperationException("The bidirection streaming is "
					+ "not supported for functions that accept non-Publisher but return Publisher: "
					+ function);
			responseObserver.onCompleted();
			throw ex;
		}
	}

	@SuppressWarnings("unchecked")
	private StreamObserver<T> biStreamReactive(StreamObserver<T> responseObserver,
			ServerCallStreamObserver<T> serverCallStreamObserver, Class<T> grpcMessageType) {
		Many<Message<byte[]>> inputStream = Sinks.many().unicast().onBackpressureBuffer();
		Flux<Message<byte[]>> inputStreamFlux = inputStream.asFlux();

		FunctionInvocationWrapper function = this.resolveFunction(null);

		Publisher<Message<byte[]>> outputPublisher = (Publisher<Message<byte[]>>) function.apply(inputStreamFlux);

		Flux.from(outputPublisher).subscribe(functionResult -> {
			T outputMessage = toGrpcMessage(functionResult, grpcMessageType);
			if (logger.isDebugEnabled()) {
				logger.debug("gRPC Server replying: " + outputMessage);
			}
			responseObserver.onNext(outputMessage);
		});

		return new StreamObserver<T>() {
			@Override
			public void onNext(T inputMessage) {
				if (logger.isDebugEnabled()) {
					logger.debug("gRPC Server receiving: " + inputMessage);
				}
				inputStream.tryEmitNext(toSpringMessage(inputMessage));
				serverCallStreamObserver.request(1);
			}

			@Override
			public void onError(Throwable t) {
				t.printStackTrace();
				inputStream.tryEmitComplete();
				responseObserver.onError(Status.UNKNOWN.withDescription("Error handling request")
						.withCause(t).asException());
			}

			@Override
			public void onCompleted() {
				logger.info("gRPC Server has finished receiving data.");
				inputStream.tryEmitComplete();
				responseObserver.onCompleted();
			}
		};
	}

	private StreamObserver<T> biStreamImperative(StreamObserver<T> responseObserver,
			ServerCallStreamObserver<T> serverCallStreamObserver,
			AtomicBoolean wasReady) {
		return new StreamObserver<T>() {

			@SuppressWarnings("unchecked")
			@Override
			public void onNext(T request) {
				try {
					Message<byte[]> message = toSpringMessage(request);
					FunctionInvocationWrapper function = resolveFunction(
							message.getHeaders());

					Message<byte[]> replyMessage = (Message<byte[]>) function
							.apply(message);

					T reply = toGrpcMessage(replyMessage, (Class<T>) request.getClass());

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
							Status.UNKNOWN.withDescription("Error handling request")
									.withCause(throwable).asException());
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

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private T toGrpcMessage(Message<byte[]> request, Class<T> grpcClass) {
		for (GrpcMessageConverter converter : this.grpcConverters) {
			GeneratedMessageV3 grpcMessage = converter.fromSpringMessage(request, grpcClass);
			if (grpcMessage != null) {
				return (T) grpcMessage;
			}
		}
		throw new IllegalStateException("Failed to convert Grpc Message to Spring Message: " + request);
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

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Message<byte[]> toSpringMessage(GeneratedMessageV3 request) {
		for (GrpcMessageConverter converter : this.grpcConverters) {
			Message<byte[]> springMessage = converter.toSpringMessage(request);
			if (springMessage != null) {
				return springMessage;
			}
		}
		throw new IllegalStateException("Failed to convert Grpc Message to Spring Message: " + request);
	}

	private FunctionInvocationWrapper resolveFunction(Map<String, Object> headers) {
		String functionDefinition = funcProperties.getDefinition();
		if (!CollectionUtils.isEmpty(headers) && headers.containsKey(FunctionProperties.FUNCTION_DEFINITION)) {
			functionDefinition = (String) headers.get(FunctionProperties.FUNCTION_DEFINITION);
		}
		FunctionInvocationWrapper function = this.functionCatalog.lookup(functionDefinition, "application/json");
		Assert.notNull(function, () -> "Failed to lookup function " + funcProperties.getDefinition());
		return function;
	}
}
