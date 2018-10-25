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

package org.springframework.cloud.function.web.mvc;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import org.springframework.cloud.function.web.RequestProcessor;
import org.springframework.cloud.function.web.RequestProcessor.FunctionWrapper;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

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

	@PostMapping(path = "/**", consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE,
			MediaType.MULTIPART_FORM_DATA_VALUE })
	@ResponseBody
	public Mono<ResponseEntity<?>> form(WebRequest request) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.post(wrapper, null, false);
	}

	@PostMapping(path = "/**")
	@ResponseBody
	public Mono<ResponseEntity<?>> post(WebRequest request,
			@RequestBody(required = false) String body) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.post(wrapper, body, false);
	}

	@PostMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<Publisher<?>>> postStream(WebRequest request,
			@RequestBody(required = false) String body) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.post(wrapper, body, true).map(response -> ResponseEntity.ok()
				.headers(response.getHeaders()).body((Publisher<?>) response.getBody()));
	}

	@GetMapping(path = "/**")
	@ResponseBody
	public Mono<ResponseEntity<?>> get(WebRequest request) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.get(wrapper);
	}

	@GetMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<Publisher<?>>> getStream(WebRequest request) {
		FunctionWrapper wrapper = wrapper(request);
		return processor.stream(wrapper).map(response -> ResponseEntity.ok()
				.headers(response.getHeaders()).body((Publisher<?>) response.getBody()));
	}

	private FunctionWrapper wrapper(WebRequest request) {
		@SuppressWarnings("unchecked")
		Function<Publisher<?>, Publisher<?>> function = (Function<Publisher<?>, Publisher<?>>) request
				.getAttribute(WebRequestConstants.FUNCTION, WebRequest.SCOPE_REQUEST);
		@SuppressWarnings("unchecked")
		Consumer<Publisher<?>> consumer = (Consumer<Publisher<?>>) request
				.getAttribute(WebRequestConstants.CONSUMER, WebRequest.SCOPE_REQUEST);
		@SuppressWarnings("unchecked")
		Supplier<Publisher<?>> supplier = (Supplier<Publisher<?>>) request
				.getAttribute(WebRequestConstants.SUPPLIER, WebRequest.SCOPE_REQUEST);
		FunctionWrapper wrapper = RequestProcessor.wrapper(function, consumer, supplier);
		for (String key : request.getParameterMap().keySet()) {
			wrapper.params().addAll(key, Arrays.asList(request.getParameterValues(key)));
		}
		for (Iterator<String> keys = request.getHeaderNames(); keys.hasNext();) {
			String key = keys.next();
			wrapper.headers().addAll(key, Arrays.asList(request.getHeaderValues(key)));
		}
		String argument = (String) request.getAttribute(WebRequestConstants.ARGUMENT,
				WebRequest.SCOPE_REQUEST);
		if (argument != null) {
			wrapper.argument(argument);
		}
		return wrapper;
	}
}
