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

package org.springframework.cloud.function.web.flux;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.cloud.function.web.util.FunctionWrapper;
import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Dave Syer
 * @author Mark Fisher
 */
@Component
public class FunctionController {

	private static Log logger = LogFactory.getLog(FunctionController.class);

	@SuppressWarnings("unchecked")
	@PostMapping(path = "/**", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> form(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return request.getFormData().doOnSuccess(params -> wrapper.getParams().addAll(params))
				.then(Mono.defer(() -> (Mono<ResponseEntity<?>>) this.doProcess(request, wrapper.getParams(), false)));
	}

	@SuppressWarnings("unchecked")
	@PostMapping(path = "/**", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> multipart(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return request.getMultipartData()
				.doOnSuccess(params -> wrapper.getParams().addAll(multi(params)))
				.then(Mono.defer(() -> (Mono<ResponseEntity<?>>) this.doProcess(request, wrapper.getParams(), false)));
	}

	private MultiValueMap<String, String> multi(MultiValueMap<String, Part> body) {
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		for (String key : body.keySet()) {
			for (Part part : body.get(key)) {
				if (part instanceof FormFieldPart) {
					FormFieldPart form = (FormFieldPart) part;
					map.add(key, form.value());
				}
			}
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	@PostMapping(path = "/**")
	@ResponseBody
	public Mono<ResponseEntity<?>> post(ServerWebExchange request,
			@RequestBody(required = false) String body) {
		Mono<ResponseEntity<?>> m = (Mono<ResponseEntity<?>>) this.doProcess(request, body, false);
		return m;
	}

	@SuppressWarnings("unchecked")
	@PostMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> postStream(ServerWebExchange request, @RequestBody(required = false) Flux<String> body) {
		return (Mono<ResponseEntity<?>>) this.doProcess(request, body, false);
	}

	@SuppressWarnings("unchecked")
	@GetMapping(path = "/**")
	@ResponseBody
	public Mono<ResponseEntity<?>> get(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return (Mono<ResponseEntity<?>>) this.doProcess(request, wrapper.getArgument(), false);
	}

	@SuppressWarnings("unchecked")
	@GetMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> getStream(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return (Mono<ResponseEntity<?>>) this.doProcess(request, wrapper.getArgument(), true);
	}

	private FunctionWrapper wrapper(ServerWebExchange request) {
		FunctionInvocationWrapper function = (FunctionInvocationWrapper) request
				.getAttribute(WebRequestConstants.HANDLER);
		FunctionWrapper wrapper = new FunctionWrapper(function);
		wrapper.setHeaders(request.getRequest().getHeaders());
		wrapper.getParams().addAll(request.getRequest().getQueryParams());
		String argument = (String) request.getAttribute(WebRequestConstants.ARGUMENT);
		if (argument != null) {
			wrapper.setArgument(argument);
		}
		return wrapper;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object doProcess(ServerWebExchange request, Object argument, boolean eventStream) {
		FunctionWrapper wrapper = wrapper(request);

		FunctionInvocationWrapper function = wrapper.getFunction();

		HttpHeaders headers = wrapper.getHeaders();

		Message<?> inputMessage = argument == null ? null : MessageBuilder.withPayload(argument).copyHeaders(headers.toSingleValueMap()).build();

		if (function.isRoutingFunction()) {
			function.setSkipOutputConversion(true);
		}

		Object input = argument == null ? Flux.empty() : (argument instanceof Publisher ? Flux.from((Publisher) argument) : inputMessage);

		Object result = function.apply(input);
		if (function.isConsumer()) {
			((Mono) result).subscribe();
			return Mono.just(ResponseEntity.accepted().headers(HeaderUtils.sanitize(headers)).build());
		}

		BodyBuilder responseOkBuilder = ResponseEntity.ok().headers(HeaderUtils.sanitize(headers));

		Publisher pResult;
		if (result instanceof Publisher) {
			pResult = (Publisher) result;
			if (eventStream) {
				return Flux.from(pResult).then(Mono.fromSupplier(() -> responseOkBuilder.body(result)));
			}

			if (pResult instanceof Flux) {
				pResult = ((Flux) pResult).onErrorContinue((e, v) -> {
					logger.error("Failed to process value: " + v, (Throwable) e);
				}).collectList();
			}
			pResult = Mono.from(pResult);
		}
		else {
			pResult = Mono.just(result);
		}

		return Mono.from(pResult).map(v -> {
			if (v instanceof Iterable) {
				List aggregatedResult = (List) ((Collection) v).stream().map(m -> {
					return m instanceof Message ? this.doProcessMessage(responseOkBuilder, (Message<?>) m) : m;
				}).collect(Collectors.toList());
				return responseOkBuilder.header("content-type", "application/json").body(aggregatedResult);
			}
			else if (v instanceof Message) {
				return responseOkBuilder.body(this.doProcessMessage(responseOkBuilder, (Message<?>) v));
			}
			else {
				return responseOkBuilder.body(v);
			}
		});
	}

	private Object doProcessMessage(BodyBuilder responseOkBuilder, Message<?> message) {
		responseOkBuilder.headers(HeaderUtils.fromMessage(message.getHeaders()));
		return message.getPayload();
	}
}
