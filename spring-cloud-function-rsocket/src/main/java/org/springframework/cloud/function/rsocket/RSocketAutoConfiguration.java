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
import java.nio.ByteBuffer;
import java.util.function.Function;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Main configuration class for components required to support RSocket integration with spring-cloud-function.
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({FunctionProperties.class, RSocketFunctionProperties.class})
public class RSocketAutoConfiguration {

	private static Log logger = LogFactory.getLog(RSocketAutoConfiguration.class);

	@Bean
	public FunctionToDestinationBinder functionToDestinationBinder(FunctionCatalog functionCatalog,
			FunctionProperties functionProperties, RSocketFunctionProperties rSocketFunctionProperties) {
		return new FunctionToDestinationBinder(functionCatalog, functionProperties, rSocketFunctionProperties);
	}


	@SuppressWarnings("rawtypes")
	private static RSocket buildRSocket(String definition, Type functionType, Function function) {
		RSocket clientRSocket = null;
//		if (isFireAndForget(functionType)) { // fire-and-forget
//			RSocket rsocket = new RSocket() { // imperative function or Function<?, Mono> = requestResponse
//				@Override
//				public Mono<Void> fireAndForget(Payload p) {
//					System.out.println("Invoking fireAndForget");
//					invokeFunction(p, function);
//					return Mono.empty();
//				}
//			};
//			return rsocket;
//		}
		if (isRequestRepply(functionType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Mapping function '" + definition + "' as RSocket `requestResponse`.");
			}

			clientRSocket = new RSocket() { // imperative function or Function<?, Mono> = requestResponse
				@SuppressWarnings("unchecked")
				@Override
				public Mono<Payload> requestResponse(Payload payload) {
					Object result = invokeFunction(payload, function);
					Mono<Payload> invocationResult = ((Mono<Message<byte[]>>) result).map(message -> DefaultPayload.create(message.getPayload()));
					return invocationResult;
				}
			};
		}
		else if (isRequestChannel(functionType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Mapping function '" + definition + "' as RSocket `requestChannel`.");
			}
			clientRSocket = new RSocket() { // imperative function or Function<?, Mono> = requestResponse
				@SuppressWarnings("unchecked")
				@Override
				public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
					return Flux.from(payloads).transform(flux -> {
						return flux.map(payload -> {
							ByteBuffer buffer = payload.getData();
							byte[] rawData = new byte[buffer.remaining()];
							buffer.get(rawData);
							if (payload.hasMetadata()) {
								String metadata = payload.getMetadataUtf8(); // TODO see what to do with it
							}
							Message<byte[]> inputMessage = MessageBuilder.withPayload(rawData).build();
							return inputMessage;
						});
					})
					.transform(function)
					.transform(resultFlux -> {
						return ((Flux<Message<byte[]>>) resultFlux).map(message -> {
							Payload p = DefaultPayload.create(message.getPayload());
							return p;
						});
					});
				}
			};
		}
		else {
			throw new UnsupportedOperationException("Only RSocket 'requestResponse' is currently supported");
		}
		Assert.notNull(clientRSocket, "Failed to create RSocket for function '" + definition + "'");
		return clientRSocket;
	}

	@SuppressWarnings("unchecked")
	private static Mono<Message<byte[]>> invokeFunction(Payload p, Function<Message<byte[]>, Mono<Message<byte[]>>> function) {
		ByteBuffer buffer = p.getData();
		byte[] rawData = new byte[buffer.remaining()];
		buffer.get(rawData);
		if (p.hasMetadata()) {
			String metadata = p.getMetadataUtf8(); // TODO see what to do with it
		}
		Message<byte[]> inputMessage = MessageBuilder.withPayload(rawData).build();
		Object result = function.apply(inputMessage);
		return result instanceof Mono ? (Mono<Message<byte[]>>) result : Mono.just((Message<byte[]>) result);
	}

	private static boolean isFireAndForget(Type functionType) {
		Type inputType = FunctionTypeUtils.getInputType(functionType, 0);
		return FunctionTypeUtils.isConsumer(functionType) && !FunctionTypeUtils.isPublisher(inputType);
	}

	private static boolean isRequestRepply(Type functionType) {
		Type inputType = FunctionTypeUtils.getInputType(functionType, 0);
		Type outputType = FunctionTypeUtils.getOutputType(functionType, 0);
		return !FunctionTypeUtils.isPublisher(inputType) && (!FunctionTypeUtils.isPublisher(outputType) || FunctionTypeUtils.isMono(outputType));
	}

	private static boolean isRequestChannel(Type functionType) {
		Type inputType = FunctionTypeUtils.getInputType(functionType, 0);
		Type outputType = FunctionTypeUtils.getOutputType(functionType, 0);
		return FunctionTypeUtils.isPublisher(inputType) && FunctionTypeUtils.isFlux(outputType);
	}

	/**
	 *
	 */
	private static class FunctionToDestinationBinder implements InitializingBean {

		private final FunctionCatalog functionCatalog;

		private final FunctionProperties functionProperties;

		private final RSocketFunctionProperties rSocketFunctionProperties;

		FunctionToDestinationBinder(FunctionCatalog functionCatalog, FunctionProperties functionProperties,
				RSocketFunctionProperties rSocketFunctionProperties) {
			this.functionCatalog = functionCatalog;
			this.functionProperties = functionProperties;
			this.rSocketFunctionProperties = rSocketFunctionProperties;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public void afterPropertiesSet() throws Exception {
			String definition = this.functionProperties.getDefinition();
			//TODO externalize content-type
			FunctionInvocationWrapper function = functionCatalog.lookup(definition, "application/json");
			if (function.isSupplier()) {
				throw new UnsupportedOperationException("Supplier is not currently supported for RSocket interaction");
			}

			Function invocableFunction = StringUtils.hasText(this.rSocketFunctionProperties.getTargetAddress())
					? new RSocketFunction(this.rSocketFunctionProperties.getTargetAddress(), this.rSocketFunctionProperties.getTargetPort(), function)
							: function;

			Type functionType = function.getFunctionType();
			RSocket rsocket = buildRSocket(definition, functionType, invocableFunction);
			RSocketServer.create(SocketAcceptor.with(rsocket))
				.bind(TcpServerTransport.create(this.rSocketFunctionProperties.getBindAddress(), this.rSocketFunctionProperties.getBindPort()))
				.subscribe(); // TODO do we need to close/dispose etc?
		}

	}
}
