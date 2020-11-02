/*
 * Copyright 2012-2020 the original author or authors.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import net.jodah.typetools.TypeResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.web.util.FunctionWebUtils;
import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.messaging.Message;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class RequestProcessor {

	private static Log logger = LogFactory.getLog(RequestProcessor.class);


	private final FunctionCatalog functionCatalog;

	private final JsonMapper mapper;

	public RequestProcessor(FunctionCatalog functionCatalog,
			ObjectProvider<JsonMapper> mapper,
			ObjectProvider<ServerCodecConfigurer> codecs) {
		this.mapper = mapper.getIfAvailable();
		this.functionCatalog = functionCatalog;
	}

	public static FunctionWrapper wrapper(FunctionInvocationWrapper function) {
		return new FunctionWrapper(function);
	}

	@SuppressWarnings("rawtypes")
	public Mono<ResponseEntity<?>> get(FunctionWrapper wrapper) {
		if (wrapper.function().isFunction()) {
			return response(wrapper, wrapper.function(), value(wrapper), true, true);
		}
		else {
			FunctionInvocationWrapper function = (wrapper.function);
			Object result = FunctionWebUtils.invokeFunction(function, null, false);
			return response(wrapper, wrapper.function(), result instanceof Publisher ? (Publisher) result : Flux.just(result), null,
					true);
		}

	}

	public Mono<ResponseEntity<?>> post(FunctionWrapper wrapper, String body,
			boolean stream) {
		FunctionInvocationWrapper function = (FunctionInvocationWrapper) wrapper.handler();
		Type itemType = getItemType(function);

		Object input = body == null  ? "" : body;

		/*
		 * We need this to ensure that imperative function which are sent array-like input
		 * can be invoked with each item and then aggregated
		 */
		if (input != null && JsonMapper.isJsonStringRepresentsCollection(input)) {
			Type type = FunctionTypeUtils.isTypeCollection(itemType)
					? ResolvableType.forType(itemType).getType()
					: ResolvableType.forClassWithGenerics(Collection.class, ResolvableType.forType(itemType)).asCollection().getType();
			input = this.mapper.fromJson((String) input, type);
		}

		return response(wrapper, input, stream);
	}

	public Mono<ResponseEntity<?>> stream(FunctionWrapper functionWrapper) {
		Publisher<?> result = functionWrapper.function.isFunction()
				? value(functionWrapper)
				: (Publisher<?>) functionWrapper.function.get();
		return stream(functionWrapper, result);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Mono<ResponseEntity<?>> response(FunctionWrapper wrapper, Object body, boolean stream) {

		FunctionInvocationWrapper function = (wrapper.function());
		Flux<?> flux;
		Class<?> inputType = function == null
				? Object.class
				: FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(function.getInputType()));
		if (MultiValueMap.class.isAssignableFrom(inputType)) {
			body = null;
			flux = Flux.just(wrapper.params());
		}
		else if (body != null) {
			if (Collection.class.isAssignableFrom(inputType)) {
				flux = Flux.just(body);
			}
			else if (body instanceof Flux) {
				flux  = Flux.from((Flux) body);
			}
			else {
				Iterable<?> iterable = body instanceof Collection
						? (Collection<?>) body
						: Collections.singletonList(body);
				flux = Flux.fromIterable(iterable);
			}
		}
		else {
			throw new IllegalStateException(
					"Failed to determine input for function call with parameters: '"
							+ wrapper.params + "' and headers: `" + wrapper.headers
							+ "`");
		}

		if (function != null) {
			flux = messages(wrapper, function, flux);
		}
		Mono<ResponseEntity<?>> responseEntityMono = null;

		if (function == null) {
			responseEntityMono = Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body("Function for provided path can not be found"));
		}
		else {
			Publisher<?> result = (Publisher<?>) FunctionWebUtils.invokeFunction(function, flux, function.isInputTypeMessage());
			if (function.isConsumer()) {
				if (result != null) {
					((Mono) result).subscribe();
				}
				logger.debug("Handled POST with consumer");
				responseEntityMono = Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build());
			}
			else {
				result = Flux.from((Publisher) result);
				logger.debug("Handled POST with function: " + function);
				if (stream) {
					responseEntityMono = stream(wrapper, result);
				}
				else {
					responseEntityMono = response(wrapper, getTargetIfRouting(wrapper, function), result,
							body == null ? null : !(body instanceof Collection), false);
				}
			}
		}
		return responseEntityMono;
	}

	private Mono<ResponseEntity<?>> response(FunctionWrapper request, Object handler,
			Publisher<?> result, Boolean single, boolean getter) {
		BodyBuilder builder = ResponseEntity.ok();
		if (((FunctionInvocationWrapper) handler).isInputTypeMessage()) {
			result = Flux.from(result)
					.map(message -> MessageUtils.unpack(handler, message))
					.doOnNext(value -> {
						addHeaders(builder, value);
					})
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
			result = Flux.from(result).onErrorContinue((e, v) -> {
				logger.error("Failed to process value: " + v, e);
			})
			.collectList();
		}
		return Mono.from(result).flatMap(body -> Mono.just(builder.body(body)));
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

	// this seem to be very relevant to AWS container tests
	private Flux<?> messages(FunctionWrapper request, Object function, Flux<?> flux) {
		Map<String, Object> headers = new HashMap<>(HeaderUtils.fromHttp(request.headers()));
		if (function instanceof FunctionInvocationWrapper) {
			headers.put("scf-func-name", ((FunctionInvocationWrapper) function).getFunctionDefinition());
		}
		return flux.map(payload -> MessageUtils.create(function, payload, headers));
	}

	private void addHeaders(BodyBuilder builder, Message<?> message) {
		builder.headers(HeaderUtils.fromMessage(message.getHeaders()));
	}

	private Mono<ResponseEntity<?>> stream(FunctionWrapper request, Publisher<?> result) {
		BodyBuilder builder = ResponseEntity.ok();
		if (((FunctionInvocationWrapper) request.handler()).isInputTypeMessage()) {
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
		FunctionInvocationWrapper function = (FunctionInvocationWrapper) handler;
		Class<?> type = function == null ? Object.class : FunctionTypeUtils
				.getRawType(FunctionTypeUtils.getGenericType(function.getInputType()));
		return Collection.class.isAssignableFrom(type) || (function != null && FunctionTypeUtils.isFlux(function.getInputType()));

	}

	private boolean isOutputSingle(Object handler) {
		FunctionInvocationWrapper function = (FunctionInvocationWrapper) handler;
		Type outputType = function.getOutputType();
		Class<?> type =  FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(outputType));
		Class<?> wrapper = function.isOutputTypePublisher() ? FunctionTypeUtils.getRawType(outputType) : type;
		if (Stream.class.isAssignableFrom(type)) {
			return false;
		}
		else {
			return wrapper == type || Mono.class.equals(wrapper)
					|| Optional.class.equals(wrapper);
		}
	}

	private Publisher<?> value(FunctionWrapper wrapper) {
		Flux<?> input = Flux.from(wrapper.argument);
		FunctionInvocationWrapper function = (wrapper.function);
		Object result = FunctionWebUtils.invokeFunction(function, input, function.isInputTypeMessage());
		return Mono.from((Publisher<?>) result);
	}

	private Type getItemType(Object function) {
		if (function == null || ((FunctionInvocationWrapper) function).getInputType() == Object.class) {
			return Object.class;
		}

		Type itemType;
		if (((FunctionInvocationWrapper) function).isInputTypePublisher() && ((FunctionInvocationWrapper) function).isInputTypeMessage()) {
			itemType = FunctionTypeUtils.getImmediateGenericType(((FunctionInvocationWrapper) function).getInputType(), 0);
			itemType = FunctionTypeUtils.getImmediateGenericType(itemType, 0);
		}
		else {
			itemType = FunctionTypeUtils.getImmediateGenericType(((FunctionInvocationWrapper) function).getInputType(), 0);
		}

		if (itemType != null) {
			return itemType;
		}

		Class<?> inputType = ((FunctionInvocationWrapper) function).isInputTypeMessage() || ((FunctionInvocationWrapper) function).isInputTypePublisher()
				? TypeResolver.resolveRawClass(itemType, null)
						: ((FunctionInvocationWrapper) function).getRawInputType();
		if (!Collection.class.isAssignableFrom(inputType)) {
			return inputType;
		}

		Type type = ((FunctionInvocationWrapper) function).getInputType();
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

		private final FunctionInvocationWrapper function;

		private final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

		private HttpHeaders headers = new HttpHeaders();

		private Publisher<String> argument;

		public FunctionWrapper(FunctionInvocationWrapper function) {
			this.function = function;
		}

		public Object handler() {
			return this.function;
		}

		public FunctionInvocationWrapper function() {
			return this.function;
		}

		@Deprecated
		public Supplier<?> supplier() {
			return this.function;
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
