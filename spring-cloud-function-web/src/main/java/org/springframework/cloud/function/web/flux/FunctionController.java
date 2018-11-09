/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.function.web.flux;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import org.springframework.cloud.function.web.RequestProcessor;
import org.springframework.cloud.function.web.RequestProcessor.FunctionWrapper;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 * @author Mark Fisher
 */
@Component
public class FunctionController {

	private RequestProcessor processor;

	public FunctionController(RequestProcessor processor) {
		this.processor = processor;
	}

	@PostMapping(path = "/**", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> form(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return request.getFormData().doOnSuccess(params -> wrapper.params(params))
				.then(Mono.defer(() -> processor.post(wrapper, null, false)));
	}

	@PostMapping(path = "/**", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> multipart(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return request.getMultipartData()
				.doOnSuccess(params -> wrapper.params(multi(params)))
				.then(Mono.defer(() -> processor.post(wrapper, null, false)));
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

	@PostMapping(path = "/**", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> post(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.post(wrapper, request);
	}

	@PostMapping(path = "/**")
	@ResponseBody
	public Mono<ResponseEntity<?>> post(ServerWebExchange request,
			@RequestBody(required = false) String body) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.post(wrapper, body, false);
	}

	@PostMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> postStream(ServerWebExchange request,
			@RequestBody(required = false) String body) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.post(wrapper, body, true);
	}

	@GetMapping(path = "/**")
	@ResponseBody
	public Mono<ResponseEntity<?>> get(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.get(wrapper);
	}

	@GetMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> getStream(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.stream(wrapper);
	}

	private FunctionWrapper wrapper(ServerWebExchange request) {
		@SuppressWarnings("unchecked")
		Function<Publisher<?>, Publisher<?>> function = (Function<Publisher<?>, Publisher<?>>) request
				.getAttribute(WebRequestConstants.FUNCTION);
		@SuppressWarnings("unchecked")
		Consumer<Publisher<?>> consumer = (Consumer<Publisher<?>>) request
				.getAttribute(WebRequestConstants.CONSUMER);
		@SuppressWarnings("unchecked")
		Supplier<Publisher<?>> supplier = (Supplier<Publisher<?>>) request
				.getAttribute(WebRequestConstants.SUPPLIER);
		FunctionWrapper wrapper = RequestProcessor.wrapper(function, consumer, supplier);
		wrapper.headers(request.getRequest().getHeaders());
		wrapper.params(request.getRequest().getQueryParams());
		String argument = (String) request.getAttribute(WebRequestConstants.ARGUMENT);
		if (argument != null) {
			wrapper.argument(argument);
		}
		return wrapper;
	}
}
