/*
 * Copyright 2018 the original author or authors.
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.messaging.Message;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

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

	@Value("${debug:${DEBUG:false}}")
	private String debug = "false";

	public RequestProcessor(JsonMapper mapper, FunctionInspector inspector,
			StringConverter converter) {
		this.mapper = mapper;
		this.inspector = inspector;
		this.converter = converter;
	}

	public static FunctionWrapper wrapper(Function<Publisher<?>, Publisher<?>> function,
			Consumer<Publisher<?>> consumer, Supplier<Publisher<?>> supplier) {
		return new FunctionWrapper(function, consumer, supplier);
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

	public Mono<ResponseEntity<?>> post(FunctionWrapper wrapper, String body,
			boolean stream) {
		Mono<ResponseEntity<?>> responseEntityMono;
		Object function = wrapper.handler();

		Object input = null;
		if (StringUtils.hasText(body)) {
			Class<?> inputType = inspector.getInputType(function);
			if (body.startsWith("[")) {
				input = mapper.toList(body, inputType);
			}
			else {
				if (inputType == String.class) {
					input = body;
				}
				else if (body.startsWith("{")) {
					input = mapper.toSingle(body, inputType);
				}
				else if (body.startsWith("\"")) {
					input = body.substring(1, body.length() - 2);
				}
				else {
					input = converter.convert(function, body);
				}
			}
		}
		responseEntityMono = post(wrapper, input, null, stream);
		return responseEntityMono;
	}

	public Mono<ResponseEntity<?>> stream(FunctionWrapper request) {
		Publisher<?> result = request.function() != null
				? value(request.function(), request.argument())
				: request.supplier().get();
		return stream(request, result);
	}

	private Mono<ResponseEntity<?>> post(FunctionWrapper wrapper, Object body,
			MultiValueMap<String, String> params, boolean stream) {

		Iterable<?> iterable = body instanceof Collection ? (List<?>) body
				: Collections.singletonList(body);

		Function<Publisher<?>, Publisher<?>> function = wrapper.function();
		Consumer<Publisher<?>> consumer = wrapper.consumer();

		MultiValueMap<String, String> form = wrapper.params();
		if (params != null) {
			form.putAll(params);
		}

		Flux<?> flux = body == null ? Flux.just(form) : Flux.fromIterable(iterable);
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
		HttpHeaders headers = new HttpHeaders();
		builder.headers(HeaderUtils.fromMessage(message.getHeaders(), headers));
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
		Class<?> type = inspector.getInputType(handler);
		Class<?> wrapper = inspector.getInputWrapper(handler);
		return Collection.class.isAssignableFrom(type) || Flux.class.equals(wrapper);
	}

	private boolean isOutputSingle(Object handler) {
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

	private Mono<?> value(Function<Publisher<?>, Publisher<?>> function, String value) {
		Object input = converter.convert(function, value);
		return Mono.from(function.apply(Flux.just(input)));
	}

	public static class FunctionWrapper {

		private final Function<Publisher<?>, Publisher<?>> function;

		private final Consumer<Publisher<?>> consumer;

		private final Supplier<Publisher<?>> supplier;

		private MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

		private HttpHeaders headers = new HttpHeaders();

		private String argument;

		public FunctionWrapper(Function<Publisher<?>, Publisher<?>> function,
				Consumer<Publisher<?>> consumer, Supplier<Publisher<?>> supplier) {
			this.function = function;
			this.consumer = consumer;
			this.supplier = supplier;
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

		public FunctionWrapper argument(String argument) {
			this.argument = argument;
			return this;
		}

		public String argument() {
			return this.argument;
		}
	}
}
