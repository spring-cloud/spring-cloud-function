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
 *
 */
public class RequestProcessor {

	private static Log logger = LogFactory.getLog(RequestProcessor.class);

	private FunctionInspector inspector;

	private StringConverter converter;

	private JsonMapper mapper;

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

	public static class FunctionWrapper {

		private Function<Publisher<?>, Publisher<?>> function;

		private Consumer<Publisher<?>> consumer;

		private Supplier<Publisher<?>> supplier;

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

	public Mono<ResponseEntity<?>> post(FunctionWrapper wrapper, String body,
			boolean stream) {

		Object function = wrapper.handler();
		if (!StringUtils.hasText(body)) {
			return post(wrapper, (List<?>) null, null, stream);
		}
		body = body.trim();
		Object input;
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
		if (input instanceof List) {
			return post(wrapper, (List<?>) input, null, stream);
		}
		return post(wrapper, Collections.singletonList(input), null, stream);
	}

	private Mono<ResponseEntity<?>> post(FunctionWrapper wrapper, List<?> body,
			MultiValueMap<String, String> params, boolean stream) {

		Function<Publisher<?>, Publisher<?>> function = wrapper.function();
		Consumer<Publisher<?>> consumer = wrapper.consumer();

		MultiValueMap<String, String> form = wrapper.params();
		if (params != null) {
			form.putAll(params);
		}

		Flux<?> flux = body == null ? Flux.just(form) : Flux.fromIterable(body);
		if (inspector.isMessage(function)) {
			flux = messages(wrapper, function == null ? consumer : function, flux);
		}
		if (function != null) {
			Flux<?> result = Flux.from(function.apply(flux));
			if (logger.isDebugEnabled()) {
				logger.debug("Handled POST with function");
			}
			if (stream) {
				return stream(wrapper, result);
			}
			return response(function, result, body == null ? null : body.size()<=1, false);
		}

		if (consumer != null) {
			consumer.accept(flux);
			if (logger.isDebugEnabled()) {
				logger.debug("Handled POST with consumer");
			}
			return Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build());
		}

		throw new IllegalArgumentException("no such function");
	}

	private Flux<?> messages(FunctionWrapper request, Object function, Flux<?> flux) {
		Map<String, Object> headers = HeaderUtils.fromHttp(request.headers());
		flux = flux.map(payload -> MessageUtils.create(function, payload, headers));
		return flux;
	}

	private void addHeaders(BodyBuilder builder, Message<?> message) {
		HttpHeaders headers = new HttpHeaders();
		builder.headers(HeaderUtils.fromMessage(message.getHeaders(), headers));
	}

	public Mono<ResponseEntity<?>> stream(FunctionWrapper request) {
		Publisher<?> result;
		if (request.function()!=null) {
			result = value(request.function(), request.argument());
		} else {
			result = supplier(request.supplier());
		}
		return stream(request, result);
	}
	
	
	private Mono<ResponseEntity<?>> stream(FunctionWrapper request, Publisher<?> result) {

		BodyBuilder builder = ResponseEntity.ok();
		if (inspector.isMessage(request.handler())) {
			result = Flux.from(result)
					.doOnNext(value -> addHeaders(builder, (Message<?>) value))
					.map(message -> MessageUtils.unpack(request.handler(), message)
							.getPayload());
		}
		
		Publisher<?> output = result;
		return Flux.from(output).then(Mono.fromSupplier(() -> builder.body(output)));

	}

	private Mono<ResponseEntity<?>> response(Object handler, Publisher<?> result,
			Boolean single, boolean getter) {

		BodyBuilder builder = ResponseEntity.ok();
		if (inspector.isMessage(handler)) {
			result = Flux.from(result)
					.doOnNext(value -> addHeaders(builder, (Message<?>) value))
					.map(message -> MessageUtils.unpack(handler, message).getPayload());
		}

		if (single != null && single && isOutputSingle(handler)) {
			result = Mono.from(result);
		}
		else if (getter && single == null && isOutputSingle(handler)) {
			result = Mono.from(result);
		}
		else if (isInputMultiple(handler) && isOutputSingle(handler)) {
			result = Mono.from(result);
		}
		Publisher<?> output = result;
		if (output instanceof Mono) {
			return Mono.from(output).flatMap(body -> Mono.just(builder.body(body)));
		}
		return Flux.from(output).collectList()
				.flatMap(body -> Mono.just(builder.body(body)));
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
		if (wrapper == type) {
			return true;
		}
		return Mono.class.equals(wrapper) || Optional.class.equals(wrapper);
	}

	private Publisher<?> supplier(Supplier<Publisher<?>> supplier) {
		Publisher<?> result = supplier.get();
		return result;
	}

	private Mono<?> value(Function<Publisher<?>, Publisher<?>> function, String value) {
		Object input = converter.convert(function, value);
		Mono<?> result = Mono.from(function.apply(Flux.just(input)));
		return result;
	}

	public Mono<ResponseEntity<?>> get(FunctionWrapper wrapper) {
		if (wrapper.function() != null) {
			return response(wrapper.function(), value(wrapper.function(), wrapper.argument()), true, true);
		}
		else {
			return response(wrapper.supplier(), supplier(wrapper.supplier()), null, true);
		}
	}
	

}
