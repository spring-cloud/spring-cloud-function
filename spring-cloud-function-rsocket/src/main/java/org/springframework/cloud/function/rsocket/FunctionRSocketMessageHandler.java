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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.rsocket.frame.FrameType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.ByteArrayDecoder;
import org.springframework.core.codec.ByteArrayEncoder;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.CompositeMessageCondition;
import org.springframework.messaging.handler.DestinationPatternsMessageCondition;
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodReturnValueHandler;
import org.springframework.messaging.handler.invocation.reactive.SyncHandlerMethodArgumentResolver;
import org.springframework.messaging.rsocket.annotation.support.RSocketFrameTypeMessageCondition;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.messaging.rsocket.annotation.support.RSocketPayloadReturnValueHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.ReflectionUtils;

/**
 * An {@link RSocketMessageHandler} extension for Spring Cloud Function specifics.
 *
 * @author Artem Bilan
 * @author Oleg Zhurakousky
 *
 * @since 3.1
 */
public class FunctionRSocketMessageHandler extends RSocketMessageHandler {

	private final FunctionCatalog functionCatalog;

	private static final Method FUNCTION_APPLY_METHOD =
		ReflectionUtils.findMethod(Function.class, "apply", (Class<?>[]) null);

	private static final RSocketFrameTypeMessageCondition REQUEST_CONDITION =
		new RSocketFrameTypeMessageCondition(
			FrameType.REQUEST_FNF,
			FrameType.REQUEST_RESPONSE,
			FrameType.REQUEST_STREAM,
			FrameType.REQUEST_CHANNEL);

	public FunctionRSocketMessageHandler(FunctionCatalog functionCatalog) {
		setHandlerPredicate((clazz) -> false);
		this.functionCatalog = functionCatalog;
	}


	@Override
	public void afterPropertiesSet() {
		setEncoders(Collections.singletonList(new ByteArrayEncoder()));
		super.afterPropertiesSet();
	}

	@Override
	public Mono<Void> handleMessage(Message<?> message) throws MessagingException {
		if (!FrameType.SETUP.equals(message.getHeaders().get("rsocketFrameType"))) {
			String destination = this.getDestination(message).value();
			Set<String> mappings = this.getDestinationLookup().keySet();
			if (!mappings.contains(destination)) {
				FunctionRSocketUtils.registerRSocketForwardingFunctionIfNecessary(destination, functionCatalog, this.getApplicationContext());
				FunctionInvocationWrapper function = functionCatalog.lookup(destination, "application/json");
				this.registerFunctionHandler(new RSocketListenerFunction(function), destination);
			}
		}

		return super.handleMessage(message);
	}

	public void registerFunctionHandler(Function<?, ?> function, String route) {
		CompositeMessageCondition condition =
			new CompositeMessageCondition(REQUEST_CONDITION,
				new DestinationPatternsMessageCondition(new String[]{ route },
					obtainRouteMatcher()));
		registerHandlerMethod(function, FUNCTION_APPLY_METHOD, condition);
	}

	@Override
	protected List<? extends HandlerMethodArgumentResolver> initArgumentResolvers() {
		return Collections.singletonList(new MessageHandlerMethodArgumentResolver());
	}

	@SuppressWarnings("unchecked")
	@Override
	protected List<? extends HandlerMethodReturnValueHandler> initReturnValueHandlers() {
		return Collections.singletonList(new FunctionRSocketPayloadReturnValueHandler((List<Encoder<?>>) getEncoders(),
			getReactiveAdapterRegistry()));
	}

	protected static final class MessageHandlerMethodArgumentResolver implements SyncHandlerMethodArgumentResolver {

		private final Decoder<byte[]> decoder = new ByteArrayDecoder();

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object resolveArgumentValue(MethodParameter parameter, Message<?> message) {
			Flux<DataBuffer> data;
			Object payload = message.getPayload();
			if (payload instanceof DataBuffer) {
				data = Flux.just((DataBuffer) payload);
			}
			else {
				data = Flux.from((Publisher<DataBuffer>) payload);
			}
			Flux<byte[]> decoded = this.decoder.decode(data, ResolvableType.forType(byte[].class), null, null);
			return MessageBuilder.createMessage(decoded, message.getHeaders());
		}

	}

	protected static final class FunctionRSocketPayloadReturnValueHandler extends RSocketPayloadReturnValueHandler {

		public FunctionRSocketPayloadReturnValueHandler(List<Encoder<?>> encoders, ReactiveAdapterRegistry registry) {
			super(encoders, registry);
		}

		@Override
		public Mono<Void> handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			Message<?> message) {

			if (returnValue instanceof Publisher<?> && !message.getHeaders().containsKey(RESPONSE_HEADER)) {
				return Mono.from((Publisher<?>) returnValue).then();
			}
			return super.handleReturnValue(returnValue, returnType, message);
		}

	}

}
