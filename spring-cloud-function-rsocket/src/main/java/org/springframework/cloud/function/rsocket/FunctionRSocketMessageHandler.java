/*
 * Copyright 2020-2021 the original author or authors.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.rsocket.frame.FrameType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.ByteArrayDecoder;
import org.springframework.core.codec.ByteArrayEncoder;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.CompositeMessageCondition;
import org.springframework.messaging.handler.DestinationPatternsMessageCondition;
import org.springframework.messaging.handler.MessageCondition;
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodReturnValueHandler;
import org.springframework.messaging.handler.invocation.reactive.SyncHandlerMethodArgumentResolver;
import org.springframework.messaging.rsocket.DefaultMetadataExtractor;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.messaging.rsocket.annotation.support.RSocketFrameTypeMessageCondition;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.messaging.rsocket.annotation.support.RSocketPayloadReturnValueHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.RouteMatcher;
import org.springframework.util.RouteMatcher.Route;
import org.springframework.util.StringUtils;
import org.springframework.web.util.pattern.PathPatternRouteMatcher;

/**
 * An {@link RSocketMessageHandler} extension for Spring Cloud Function specifics.
 *
 * @author Artem Bilan
 * @author Oleg Zhurakousky
 *
 * @since 3.1
 */
class FunctionRSocketMessageHandler extends RSocketMessageHandler {

	public static final String RECONCILED_LOOKUP_DESTINATION_HEADER = "reconciledLookupDestination";

	private final FunctionCatalog functionCatalog;

	private final FunctionProperties functionProperties;

	private final Field headersField;

	private static final Method FUNCTION_APPLY_METHOD =
		ReflectionUtils.findMethod(Function.class, "apply", (Class<?>[]) null);

	private static final RSocketFrameTypeMessageCondition REQUEST_CONDITION =
		new RSocketFrameTypeMessageCondition(
			FrameType.REQUEST_FNF,
			FrameType.REQUEST_RESPONSE,
			FrameType.REQUEST_STREAM,
			FrameType.REQUEST_CHANNEL);

	FunctionRSocketMessageHandler(FunctionCatalog functionCatalog, FunctionProperties functionProperties) {
		setHandlerPredicate((clazz) -> false);
		this.functionCatalog = functionCatalog;
		this.functionProperties = functionProperties;
		this.headersField = ReflectionUtils.findField(MessageHeaders.class, "headers");
		this.headersField.setAccessible(true);
	}


	@Override
	public void afterPropertiesSet() {
		setEncoders(Collections.singletonList(new ByteArrayEncoder()));
		super.afterPropertiesSet();
	}

	@SuppressWarnings("unchecked")
	@Override
	public MetadataExtractor getMetadataExtractor() {
		return new HeadersAwareMetadataExtractor((List<Decoder<?>>) this.getDecoders());
	}

	/**
	 * Will check if there is a function handler registered for destination before proceeding.
	 * This typically happens when user avoids using 'spring.cloud.function.definition' property.
	 */
	@Override
	public Mono<Void> handleMessage(Message<?> message) throws MessagingException {
		if (!FrameType.SETUP.equals(message.getHeaders().get("rsocketFrameType"))) {
			String destination = this.discoverAndInjectDestinationHeader(message);

			Set<String> mappings = this.getDestinationLookup().keySet();
			if (!mappings.contains(destination)) {
				FunctionInvocationWrapper function = FunctionRSocketUtils
						.registerFunctionForDestination(destination, this.functionCatalog, this.getApplicationContext());
				this.registerFunctionHandler(new RSocketListenerFunction(function), destination);
			}
		}

		return super.handleMessage(message);
	}

	@Override
	protected RouteMatcher.Route getDestination(Message<?> message) {
		RouteMatcher.Route reconsiledDestination = (RouteMatcher.Route) message.getHeaders().get(RECONCILED_LOOKUP_DESTINATION_HEADER);
		return reconsiledDestination == null ? super.getDestination(message) : reconsiledDestination;
	}

	@Override
	protected CompositeMessageCondition getMatchingMapping(CompositeMessageCondition mapping, Message<?> message) {
		List<MessageCondition<?>> result = new ArrayList<>(mapping.getMessageConditions().size());
		for (MessageCondition<?> condition : mapping.getMessageConditions()) {
			MessageCondition<?> matchingCondition = condition instanceof DestinationPatternsMessageCondition
					? condition
							: (MessageCondition<?>) condition.getMatchingCondition(message);
			if (matchingCondition == null) {
				return null;
			}
			result.add(matchingCondition);
		}
		return new CompositeMessageCondition(result.toArray(new MessageCondition[0]));
	}

	void registerFunctionHandler(Function<?, ?> function, String route) {
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

	private String discoverAndInjectDestinationHeader(Message<?> message) {

		String destination;
		if (!CollectionUtils.isEmpty(this.getApplicationContext().getBeansOfType(MessageRoutingCallback.class))) {
			destination = RoutingFunction.FUNCTION_NAME;
		}
		else if (StringUtils.hasText(this.functionProperties.getRoutingExpression())) {
			destination = RoutingFunction.FUNCTION_NAME;
			this.updateMessageHeaders(message, destination);
		}
		else {
			Route route = (Route) message.getHeaders().get(DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER);
			destination = route.value();
			if (!StringUtils.hasText(destination)) {
				destination = this.functionProperties.getDefinition();
				this.updateMessageHeaders(message, destination);
			}
		}

		if (!StringUtils.hasText(destination) && logger.isDebugEnabled()) {
			logger.debug("Failed to discover function definition. Neither "
				+ "`spring.cloud.function.definition`, nor `.route(<function.definition>)`, nor "
				+ "`spring.cloud.function.routing-expression` were provided. Will use empty string "
				+ "for lookup, which will work only if there is one function in Function Catalog");
		}
		return destination;
	}

	@SuppressWarnings("unchecked")
	private void updateMessageHeaders(Message<?> message, String destination) {
		Map<String, Object> headersMap = (Map<String, Object>) ReflectionUtils
				.getField(this.headersField, message.getHeaders());
		PathPatternRouteMatcher matcher = new PathPatternRouteMatcher();
		headersMap.put(RECONCILED_LOOKUP_DESTINATION_HEADER, matcher.parseRoute(destination));
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

	/**
	 * This metadata extractor will ensure that any JSON data passed
	 * via metadata will be copied into Message headers.
	 */
	private static class HeadersAwareMetadataExtractor extends DefaultMetadataExtractor {
		HeadersAwareMetadataExtractor(List<Decoder<?>> decoders) {
			super(decoders);
			super.metadataToExtract(MimeTypeUtils.APPLICATION_JSON,
					new ParameterizedTypeReference<Map<String, String>>() {
					}, (jsonMap, outputMap) -> outputMap.putAll(jsonMap)
			);
		}
	}

}
