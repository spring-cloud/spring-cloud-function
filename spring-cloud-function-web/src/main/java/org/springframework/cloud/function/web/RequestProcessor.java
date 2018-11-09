/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.web;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.function.core.FluxWrapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Hints;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class RequestProcessor {

	private static Log logger = LogFactory.getLog(RequestProcessor.class);

	private final FunctionInspector inspector;

	private final StringConverter converter;

	private final JsonMapper mapper;

	private final List<HttpMessageReader<?>> messageReaders;

	public RequestProcessor(FunctionInspector inspector,
			ObjectProvider<JsonMapper> mapper, StringConverter converter,
			ObjectProvider<ServerCodecConfigurer> codecs) {
		this.mapper = mapper.getIfAvailable();
		this.inspector = inspector;
		this.converter = converter;
		ServerCodecConfigurer source = codecs.getIfAvailable();
		this.messageReaders = source == null ? null : source.getReaders();
	}

	public static FunctionWrapper wrapper(
			Function<? extends Publisher<?>, ? extends Publisher<?>> function,
			Consumer<? extends Publisher<?>> consumer,
			Supplier<? extends Publisher<?>> supplier) {
		return new FunctionWrapper(function, consumer, supplier);
	}

	public static FunctionWrapper wrapper(
			Function<? extends Publisher<?>, ? extends Publisher<?>> function) {
		return new FunctionWrapper(function, null, null);
	}

	public Mono<ResponseEntity<?>> get(FunctionWrapper wrapper) {
		if (wrapper.function() != null) {
			return response(wrapper, wrapper.function(),
					value(wrapper.function(), wrapper.argument()), true, true);
		}
		else {
			return response(wrapper, wrapper.supplier(), wrapper.supplier().get(), null,
					true);
		}
	}

	public Mono<ResponseEntity<?>> post(FunctionWrapper wrapper,
			ServerWebExchange exchange) {
		return Mono.from(body(wrapper.handler(), exchange))
				.flatMap(body -> response(wrapper, body, false));
	}

	public Mono<ResponseEntity<?>> post(FunctionWrapper wrapper, String body,
			boolean stream) {
		Object function = wrapper.handler();
		Class<?> inputType = inspector.getInputType(function);
		Type itemType = getItemType(function);

		Object input = body;
		if (StringUtils.hasText(body) && this.mapper != null) {
			if (body.startsWith("[")) {
				Class<?> collectionType = Collection.class.isAssignableFrom(inputType)
						? inputType
						: Collection.class;
				input = mapper.toObject(body,
						ResolvableType
								.forClassWithGenerics(collectionType, (Class<?>) itemType)
								.getType());
			}
			else {
				if (inputType == String.class) {
					input = body;
				}
				else if (body.startsWith("{")) {
					input = mapper.toObject(body, inputType);
				}
				else if (body.startsWith("\"")) {
					input = body.substring(1, body.length() - 2);
				}
				else {
					input = converter.convert(function, body);
				}
			}
		}
		return response(wrapper, input, stream);
	}

	public Mono<ResponseEntity<?>> stream(FunctionWrapper request) {
		Publisher<?> result = request.function() != null
				? value(request.function(), request.argument())
				: request.supplier().get();
		return stream(request, result);
	}

	private Mono<ResponseEntity<?>> response(FunctionWrapper wrapper, Object body,
			boolean stream) {

		Iterable<?> iterable = body instanceof Collection ? (Collection<?>) body
				: (body instanceof Set ? Collections.singleton(body)
						: Collections.singletonList(body));

		Function<Publisher<?>, Publisher<?>> function = wrapper.function();
		Consumer<Publisher<?>> consumer = wrapper.consumer();

		MultiValueMap<String, String> form = wrapper.params();

		boolean inputIsCollection = Collection.class
				.isAssignableFrom(inspector.getInputType(wrapper.handler()));
		Flux<?> flux = body == null ? Flux.just(form)
				: inputIsCollection ? Flux.just(body) : Flux.fromIterable(iterable);
		if (inspector.isMessage(function)) {
			flux = messages(wrapper, function == null ? consumer : function, flux);
		}
		Mono<ResponseEntity<?>> responseEntityMono = null;
		if (function != null) {
			Flux<?> result = Flux.from(function.apply(flux));
			logger.debug("Handled POST with function");
			if (stream) {
				responseEntityMono = stream(wrapper, result);
			}
			else {
				responseEntityMono = response(wrapper, function, result,
						body == null ? null : !(body instanceof Collection), false);
			}
		}
		else if (consumer != null) {
			consumer.accept(flux);
			logger.debug("Handled POST with consumer");
			responseEntityMono = Mono
					.just(ResponseEntity.status(HttpStatus.ACCEPTED).build());
		}
		return responseEntityMono;
	}

	private Flux<?> messages(FunctionWrapper request, Object function, Flux<?> flux) {
		Map<String, Object> headers = HeaderUtils.fromHttp(request.headers());
		return flux.map(payload -> MessageUtils.create(function, payload, headers));
	}

	private void addHeaders(BodyBuilder builder, Message<?> message) {
		builder.headers(HeaderUtils.fromMessage(message.getHeaders()));
	}

	private Mono<ResponseEntity<?>> stream(FunctionWrapper request, Publisher<?> result) {
		BodyBuilder builder = ResponseEntity.ok();
		if (inspector.isMessage(request.handler())) {
			result = Flux.from(result)
					.doOnNext(value -> addHeaders(builder, (Message<?>) value))
					.map(message -> MessageUtils.unpack(request.handler(), message)
							.getPayload());
		}
		else {
			builder.headers(HeaderUtils.sanitize(request.headers()));
		}

		Publisher<?> output = result;
		return Flux.from(output).then(Mono.fromSupplier(() -> builder.body(output)));
	}

	private Mono<ResponseEntity<?>> response(FunctionWrapper request, Object handler,
			Publisher<?> result, Boolean single, boolean getter) {

		BodyBuilder builder = ResponseEntity.ok();
		if (inspector.isMessage(handler)) {
			result = Flux.from(result)
					.map(message -> MessageUtils.unpack(handler, message))
					.doOnNext(value -> addHeaders(builder, value))
					.map(message -> message.getPayload());
		}
		else {
			builder.headers(HeaderUtils.sanitize(request.headers()));
		}

		if (isOutputSingle(handler)
				&& (single != null && single || getter || isInputMultiple(handler))) {
			result = Mono.from(result);
		}

		if (result instanceof Flux) {
			result = Flux.from(result).collectList();
		}
		return Mono.from(result).flatMap(body -> Mono.just(builder.body(body)));
	}

	private boolean isInputMultiple(Object handler) {
		if (handler instanceof FluxWrapper) {
			handler = ((FluxWrapper<?>) handler).getTarget();
		}
		Class<?> type = inspector.getInputType(handler);
		Class<?> wrapper = inspector.getInputWrapper(handler);
		return Collection.class.isAssignableFrom(type) || Flux.class.equals(wrapper);
	}

	private boolean isOutputSingle(Object handler) {
		if (handler instanceof FluxWrapper) {
			handler = ((FluxWrapper<?>) handler).getTarget();
		}
		Class<?> type = inspector.getOutputType(handler);
		Class<?> wrapper = inspector.getOutputWrapper(handler);
		if (Stream.class.isAssignableFrom(type)) {
			return false;
		}
		else {
			return wrapper == type || Mono.class.equals(wrapper)
					|| Optional.class.equals(wrapper);
		}
	}

	private Publisher<?> body(Object handler, ServerWebExchange exchange) {

		ResolvableType elementType = ResolvableType
				.forClass(this.inspector.getInputType(handler));
		ResolvableType actualType = elementType;
		Class<?> resolvedType = elementType.resolve();
		ReactiveAdapter adapter = (resolvedType != null
				? getAdapterRegistry().getAdapter(resolvedType)
				: null);

		ServerHttpRequest request = exchange.getRequest();
		ServerHttpResponse response = exchange.getResponse();

		MediaType contentType = request.getHeaders().getContentType();
		MediaType mediaType = (contentType != null ? contentType
				: MediaType.APPLICATION_OCTET_STREAM);

		if (logger.isDebugEnabled()) {
			logger.debug(exchange.getLogPrefix() + (contentType != null
					? "Content-Type:" + contentType
					: "No Content-Type, using " + MediaType.APPLICATION_OCTET_STREAM));
		}
		boolean isBodyRequired = (adapter != null && !adapter.supportsEmpty());

		MethodParameter bodyParam = new MethodParameter(handlerMethod(handler), 0);
		for (HttpMessageReader<?> reader : getMessageReaders()) {
			if (reader.canRead(elementType, mediaType)) {
				Map<String, Object> readHints = Hints.from(Hints.LOG_PREFIX_HINT,
						exchange.getLogPrefix());
				if (adapter != null && adapter.isMultiValue()) {
					if (logger.isDebugEnabled()) {
						logger.debug(
								exchange.getLogPrefix() + "0..N [" + elementType + "]");
					}
					Flux<?> flux = reader.read(actualType, elementType, request, response,
							readHints);
					flux = flux.onErrorResume(
							ex -> Flux.error(handleReadError(bodyParam, ex)));
					if (isBodyRequired) {
						flux = flux.switchIfEmpty(
								Flux.error(() -> handleMissingBody(bodyParam)));
					}
					return Mono.just(adapter.fromPublisher(flux));
				}
				else {
					// Single-value (with or without reactive type wrapper)
					if (logger.isDebugEnabled()) {
						logger.debug(
								exchange.getLogPrefix() + "0..1 [" + elementType + "]");
					}
					Mono<?> mono = reader.readMono(actualType, elementType, request,
							response, readHints);
					mono = mono.onErrorResume(
							ex -> Mono.error(handleReadError(bodyParam, ex)));
					if (isBodyRequired) {
						mono = mono.switchIfEmpty(
								Mono.error(() -> handleMissingBody(bodyParam)));
					}
					return (adapter != null ? Mono.just(adapter.fromPublisher(mono))
							: Mono.from(mono));
				}
			}
		}

		return Mono.error(new UnsupportedMediaTypeStatusException(mediaType,
				Arrays.asList(MediaType.APPLICATION_JSON), elementType));
	}

	private Method handlerMethod(Object handler) {
		return ReflectionUtils.findMethod(handler.getClass(), "apply", (Class<?>[]) null);
	}

	public List<HttpMessageReader<?>> getMessageReaders() {
		return this.messageReaders;
	}

	private Throwable handleReadError(MethodParameter parameter, Throwable ex) {
		return (ex instanceof DecodingException
				? new ServerWebInputException("Failed to read HTTP message", parameter,
						ex)
				: ex);
	}

	private ServerWebInputException handleMissingBody(MethodParameter param) {
		return new ServerWebInputException(
				"Request body is missing: " + param.getExecutable().toGenericString());
	}

	private ReactiveAdapterRegistry getAdapterRegistry() {
		return ReactiveAdapterRegistry.getSharedInstance();
	}

	private Publisher<?> value(Function<Publisher<?>, Publisher<?>> function,
			Publisher<String> value) {
		Flux<?> input = Flux.from(value).map(body -> converter.convert(function, body));
		return Mono.from(function.apply(input));
	}

	private Object getTargetFunction(Object function) {
		// we need to get the actual un-fluxed function so we can interrogate for types
		Object target = inspector.getRegistration(function).getTarget();
		if (target instanceof FluxWrapper) {
			target = ((FluxWrapper<?>) target).getTarget();
		}
		return target;
	}

	private Type getItemType(Object function) {
		Class<?> inputType = inspector.getInputType(function);
		if (!Collection.class.isAssignableFrom(inputType)) {
			return inputType;
		}
		Type type = inspector.getRegistration(this.getTargetFunction(function)).getType()
				.getType();
		if (type instanceof ParameterizedType) {
			type = ((ParameterizedType) type).getActualTypeArguments()[0];
		}
		else {
			for (Type iface : ((Class<?>) type).getGenericInterfaces()) {
				if (iface.getTypeName().startsWith("java.util.function")) {
					type = ((ParameterizedType) iface).getActualTypeArguments()[0];
					break;
				}
			}
		}
		if (type instanceof ParameterizedType) {
			type = ((ParameterizedType) type).getActualTypeArguments()[0];
		}
		else {
			type = inputType;
		}
		return type;
	}

	public static class FunctionWrapper {

		private final Function<Publisher<?>, Publisher<?>> function;

		private final Consumer<Publisher<?>> consumer;

		private final Supplier<Publisher<?>> supplier;

		private final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

		private HttpHeaders headers = new HttpHeaders();

		private Publisher<String> argument;

		@SuppressWarnings("unchecked")
		public FunctionWrapper(
				Function<? extends Publisher<?>, ? extends Publisher<?>> function,
				Consumer<? extends Publisher<?>> consumer,
				Supplier<? extends Publisher<?>> supplier) {
			this.function = (Function<Publisher<?>, Publisher<?>>) function;
			this.consumer = (Consumer<Publisher<?>>) consumer;
			this.supplier = (Supplier<Publisher<?>>) supplier;
		}

		public Object handler() {
			return function != null ? function : consumer != null ? consumer : supplier;
		}

		public Function<Publisher<?>, Publisher<?>> function() {
			return this.function;
		}

		public Consumer<Publisher<?>> consumer() {
			return this.consumer;
		}

		public Supplier<Publisher<?>> supplier() {
			return this.supplier;
		}

		public MultiValueMap<String, String> params() {
			return params;
		}

		public HttpHeaders headers() {
			return this.headers;
		}

		public FunctionWrapper headers(HttpHeaders headers) {
			this.headers = headers;
			return this;
		}

		public FunctionWrapper params(MultiValueMap<String, String> params) {
			this.params.addAll(params);
			return this;
		}

		public FunctionWrapper argument(Publisher<String> argument) {
			this.argument = argument;
			return this;
		}

		public FunctionWrapper argument(String argument) {
			this.argument = Mono.just(argument);
			return this;
		}

		public Publisher<String> argument() {
			return this.argument;
		}
	}
}
