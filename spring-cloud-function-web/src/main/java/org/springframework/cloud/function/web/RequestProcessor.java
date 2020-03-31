/*
 * Copyright 2012-2019 the original author or authors.
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.function.core.FluxConsumer;
import org.springframework.cloud.function.core.FluxedConsumer;
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
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class RequestProcessor {

	private static Log logger = LogFactory.getLog(RequestProcessor.class);

	private final FunctionInspector inspector;

	private final FunctionCatalog functionCatalog;

	private final StringConverter converter;

	private final JsonMapper mapper;

	private final List<HttpMessageReader<?>> messageReaders;

	public RequestProcessor(FunctionInspector inspector,
			FunctionCatalog functionCatalog,
			ObjectProvider<JsonMapper> mapper, StringConverter converter,
			ObjectProvider<ServerCodecConfigurer> codecs) {
		this.mapper = mapper.getIfAvailable();
		this.inspector = inspector;
		this.functionCatalog = functionCatalog;
		this.converter = converter;
		ServerCodecConfigurer source = codecs.getIfAvailable();
		this.messageReaders = source == null ? null : source.getReaders();
	}

	public static FunctionWrapper wrapper(
			Function<? extends Publisher<?>, ? extends Publisher<?>> function,
			Consumer<? extends Publisher<?>> consumer,
			Supplier<? extends Publisher<?>> supplier) {
		return new FunctionWrapper(function, supplier);
	}

	public static FunctionWrapper wrapper(
			Function<? extends Publisher<?>, ? extends Publisher<?>> function) {
		return new FunctionWrapper(function, null);
	}

	@SuppressWarnings("rawtypes")
	public Mono<ResponseEntity<?>> get(FunctionWrapper wrapper) {
		if (wrapper.function() != null) {
			return response(wrapper, wrapper.function(), value(wrapper), true, true);
		}
		else {
			Object result = wrapper.supplier().get();
			return response(wrapper, wrapper.supplier(), result instanceof Publisher ? (Publisher) result : Flux.just(result), null,
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
		Class<?> inputType = this.inspector.getInputType(function);
		Type itemType = getItemType(function);

		Object input = body == null && inputType.isAssignableFrom(String.class) ? "" : body;

		if ((isInputMultiple(this.getTargetIfRouting(wrapper, function))  || !(function instanceof RoutingFunction))
				&& input != null) { // TODO rework. . . pretty ugly
			if (this.shouldUseJsonConversion((String) input, wrapper.headers.getContentType())) {
				Type jsonType = body.startsWith("[")
						&& Collection.class.isAssignableFrom(inputType)
						|| body.startsWith("{") ? inputType : Collection.class;
				if (body.startsWith("[") && itemType instanceof Class) {
					jsonType = ResolvableType.forClassWithGenerics((Class<?>) jsonType,
							(Class<?>) itemType).getType();
				}
				input = this.mapper.toObject((String) input, jsonType);
			}
			else {
				input = this.converter.convert(function, (String) input);
			}
		}

		return response(wrapper, input, stream);
	}

	public Mono<ResponseEntity<?>> stream(FunctionWrapper request) {
		Publisher<?> result = request.function() != null
				? value(request)
				: request.supplier().get();
		return stream(request, result);
	}

	private boolean shouldUseJsonConversion(String body, MediaType contentType) {
		return (body.startsWith("[") || body.startsWith("{"))
				&& (contentType == null || (contentType != null
						&& !"text".equalsIgnoreCase(contentType.getType())));
	}

	private List<HttpMessageReader<?>> getMessageReaders() {
		return this.messageReaders;
	}

	private Mono<ResponseEntity<?>> response(FunctionWrapper request, Object handler,
			Publisher<?> result, Boolean single, boolean getter) {

		BodyBuilder builder = ResponseEntity.ok();
		if (this.inspector.isMessage(handler)) {
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

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Mono<ResponseEntity<?>> response(FunctionWrapper wrapper, Object body,
			boolean stream) {

		Function function = wrapper.function();

		Flux<?> flux;
		if (body != null) {
			if (Collection.class
					.isAssignableFrom(this.inspector.getInputType(wrapper.handler()))) {
				flux = Flux.just(body);
			}
			else {
				Iterable<?> iterable = body instanceof Collection ? (Collection<?>) body
						: (body instanceof Set ? Collections.singleton(body)
								: Collections.singletonList(body));
				flux = Flux.fromIterable(iterable);
			}
		}
		else if (MultiValueMap.class
				.isAssignableFrom(this.inspector.getInputType(wrapper.handler()))) {
			flux = Flux.just(wrapper.params());
		}
		else {
			throw new IllegalStateException(
					"Failed to determine input for function call with parameters: '"
							+ wrapper.params + "' and headers: `" + wrapper.headers
							+ "`");
		}

		if (this.inspector.isMessage(function)) {
			flux = messages(wrapper, function, flux);
		}
		Mono<ResponseEntity<?>> responseEntityMono = null;

		if (function == null) {
			responseEntityMono = Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body("Function for provided path can not be found"));
		}
		else if (function instanceof FluxedConsumer || function instanceof FluxConsumer) {
			((Mono<?>) function.apply(flux)).subscribe();
			logger.debug("Handled POST with consumer");
			responseEntityMono = Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build());
		}
		else if (function instanceof FunctionInvocationWrapper) {
			Publisher<?> result = (Publisher<?>) function.apply(flux);
			if (((FunctionInvocationWrapper) function).isConsumer()) {
				if (result != null) {
					((Mono) result).subscribe();
				}
				logger.debug("Handled POST with consumer");
				responseEntityMono = Mono
						.just(ResponseEntity.status(HttpStatus.ACCEPTED).build());
			}
			else {
				result = Flux.from((Publisher) result);
				logger.debug("Handled POST with function");
				if (stream) {
					responseEntityMono = stream(wrapper, result);
				}
				else {
					responseEntityMono = response(wrapper, getTargetIfRouting(wrapper, function), result,
							body == null ? null : !(body instanceof Collection), false);
				}
			}
		}
		else {
			Flux<?> result = Flux.from((Publisher) function.apply(flux));
			logger.debug("Handled POST with function");
			if (stream) {
				responseEntityMono = stream(wrapper, result);
			}
			else {
				responseEntityMono = response(wrapper, getTargetIfRouting(wrapper, function), result,
						body == null ? null : !(body instanceof Collection), false);
			}
		}
		return responseEntityMono;
	}

	/*
	 * Called when building response and returns the actual
	 * target function in case the current function is RoutingFunction.
	 * This is necessary to determine the type of the output (e.g., Flux =
	 * multiple or Mono = single etc). See isOutputSingle(..).
	 */
	private Object getTargetIfRouting(FunctionWrapper wrapper, Object function) {
		if (function instanceof RoutingFunction) {
			String name = wrapper.headers.get("function.name").iterator().next();
			function = this.functionCatalog.lookup(name);
		}
		return function;
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
		if (this.inspector.isMessage(request.handler())) {
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



	private boolean isInputMultiple(Object handler) {
		Class<?> type = this.inspector.getInputType(handler);
		Class<?> wrapper = this.inspector.getInputWrapper(handler);
		return Collection.class.isAssignableFrom(type) || Flux.class.equals(wrapper);
	}

	private boolean isOutputSingle(Object handler) {
		Class<?> type = this.inspector.getOutputType(handler);
		Class<?> wrapper = this.inspector.getOutputWrapper(handler);
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
				? getAdapterRegistry().getAdapter(resolvedType) : null);

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

	private Throwable handleReadError(MethodParameter parameter, Throwable ex) {
		return (ex instanceof DecodingException ? new ServerWebInputException(
				"Failed to read HTTP message", parameter, ex) : ex);
	}

	private ServerWebInputException handleMissingBody(MethodParameter param) {
		return new ServerWebInputException(
				"Request body is missing: " + param.getExecutable().toGenericString());
	}

	private ReactiveAdapterRegistry getAdapterRegistry() {
		return ReactiveAdapterRegistry.getSharedInstance();
	}

	private Publisher<?> value(FunctionWrapper wrapper) {
		Flux<?> input = Flux.from(wrapper.argument)
				.map(body -> this.converter.convert(wrapper.function, body));
		if (this.inspector.isMessage(wrapper.function)) {
			input = messages(wrapper, wrapper.function, input);
		}
		return Mono.from(wrapper.function.apply(input));
	}

	private Type getItemType(Object function) {
		Class<?> inputType = this.inspector.getInputType(function);
		if (!Collection.class.isAssignableFrom(inputType)) {
			return inputType;
		}
		Type type = this.inspector.getRegistration(function).getType().getType();
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

	/**
	 * Wrapper for functions.
	 */
	public static class FunctionWrapper {

		private final Function<Publisher<?>, Publisher<?>> function;

		private final Supplier<Publisher<?>> supplier;

		private final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

		private HttpHeaders headers = new HttpHeaders();

		private Publisher<String> argument;

		@SuppressWarnings("unchecked")
		public FunctionWrapper(
				Function<? extends Publisher<?>, ? extends Publisher<?>> function,
				Supplier<? extends Publisher<?>> supplier) {
			this.function = (Function<Publisher<?>, Publisher<?>>) function;
			this.supplier = (Supplier<Publisher<?>>) supplier;
		}

		public Object handler() {
			return this.function != null
					? this.function
					: this.supplier;
		}

		public Function<Publisher<?>, Publisher<?>> function() {
			return this.function;
		}

		public Supplier<Publisher<?>> supplier() {
			return this.supplier;
		}

		public MultiValueMap<String, String> params() {
			return this.params;
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
