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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.cloud.function.web.util.FunctionWebRequestProcessingHelper;
import org.springframework.cloud.function.web.util.FunctionWrapper;
import org.springframework.http.HttpHeaders;
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

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
@Component
public class FunctionController {

	@SuppressWarnings("unchecked")
	@PostMapping(path = "/**", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> form(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return request.getFormData().doOnSuccess(params -> wrapper.getParams().addAll(params))
				.then(Mono.defer(() -> (Mono<ResponseEntity<?>>) FunctionWebRequestProcessingHelper
						.processRequest(wrapper, wrapper.getParams(), false)));
	}

	@SuppressWarnings("unchecked")
	@PostMapping(path = "/**", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> multipart(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return request.getMultipartData()
				.doOnSuccess(params -> wrapper.getParams().addAll(multi(params)))
				.then(Mono.defer(() -> (Mono<ResponseEntity<?>>) FunctionWebRequestProcessingHelper
						.processRequest(wrapper, wrapper.getParams(), false)));
	}

	@SuppressWarnings("unchecked")
	@PostMapping(path = "/**")
	@ResponseBody
	public Mono<ResponseEntity<?>> post(ServerWebExchange request,
			@RequestBody(required = false) String body) {
		return (Mono<ResponseEntity<?>>) FunctionWebRequestProcessingHelper.processRequest(wrapper(request), body, false);
	}

	@SuppressWarnings("unchecked")
	@PostMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> postStream(ServerWebExchange request, @RequestBody(required = false) Flux<String> body) {
		return (Mono<ResponseEntity<?>>) FunctionWebRequestProcessingHelper.processRequest(wrapper(request), body, false);
	}

	@SuppressWarnings("unchecked")
	@GetMapping(path = "/**")
	@ResponseBody
	public Mono<ResponseEntity<?>> get(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return (Mono<ResponseEntity<?>>) FunctionWebRequestProcessingHelper.processRequest(wrapper, wrapper.getArgument(), false);
	}

	@SuppressWarnings("unchecked")
	@GetMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<?>> getStream(ServerWebExchange request) {
		FunctionWrapper wrapper = wrapper(request);
		return (Mono<ResponseEntity<?>>) FunctionWebRequestProcessingHelper.processRequest(wrapper, wrapper.getArgument(), true);
	}

	private FunctionWrapper wrapper(ServerWebExchange request) {
		FunctionInvocationWrapper function = (FunctionInvocationWrapper) request
				.getAttribute(WebRequestConstants.HANDLER);
		HttpHeaders headers = HttpHeaders.writableHttpHeaders(request.getRequest().getHeaders());
		headers.set("uri", request.getRequest().getURI().toString());
		FunctionWrapper wrapper = new FunctionWrapper(function);
		wrapper.setHeaders(headers);
		wrapper.getParams().addAll(request.getRequest().getQueryParams());
		String argument = (String) request.getAttribute(WebRequestConstants.ARGUMENT);
		if (argument != null) {
			wrapper.setArgument(argument);
		}
		return wrapper;
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
}
