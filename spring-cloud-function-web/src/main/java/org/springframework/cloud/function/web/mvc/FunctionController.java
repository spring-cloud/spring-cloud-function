/*
 * Copyright 2012-2021 the original author or authors.
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

package org.springframework.cloud.function.web.mvc;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.cloud.function.web.util.FunctionWebRequestProcessingHelper;
import org.springframework.cloud.function.web.util.FunctionWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
@Component
public class FunctionController {

	@PostMapping(path = "/**", consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE,
			MediaType.MULTIPART_FORM_DATA_VALUE })
	@ResponseBody
	public Object form(WebRequest request) {
		FunctionWrapper wrapper = wrapper(request);

		if (((ServletWebRequest) request).getRequest() instanceof StandardMultipartHttpServletRequest) {
			MultiValueMap<String, MultipartFile> multiFileMap = ((StandardMultipartHttpServletRequest) ((ServletWebRequest) request)
														.getRequest()).getMultiFileMap();
			if (!CollectionUtils.isEmpty(multiFileMap)) {
				List<Message<MultipartFile>> files = multiFileMap.values().stream().flatMap(v -> v.stream())
						.map(file -> MessageBuilder.withPayload(file).copyHeaders(wrapper.getHeaders()).build())
						.collect(Collectors.toList());
				FunctionInvocationWrapper function = wrapper.getFunction();

				Publisher<?> result = (Publisher<?>) function.apply(Flux.fromIterable(files));
				BodyBuilder builder = ResponseEntity.ok();
				if (result instanceof Flux) {
					result = Flux.from(result).map(message -> {
						return message instanceof Message ? ((Message<?>) message).getPayload() : message;
					}).collectList();
				}
				return Mono.from(result).flatMap(body -> Mono.just(builder.body(body)));
			}
		}
		return FunctionWebRequestProcessingHelper.processRequest(wrapper, wrapper.getParams(), false);
	}

	@SuppressWarnings("unchecked")
	@PostMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<Publisher<?>>> postStream(WebRequest request,
			@RequestBody(required = false) String body) {
		String argument = StringUtils.hasText(body) ? body : "";
		return ((Mono<ResponseEntity<?>>) FunctionWebRequestProcessingHelper.processRequest(wrapper(request), argument, true)).map(response -> ResponseEntity.ok()
				.headers(response.getHeaders()).body((Publisher<?>) response.getBody()));
	}

	@SuppressWarnings("unchecked")
	@GetMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public Mono<ResponseEntity<Publisher<?>>> getStream(WebRequest request) {
		FunctionWrapper wrapper = wrapper(request);
		return ((Mono<ResponseEntity<?>>) FunctionWebRequestProcessingHelper
				.processRequest(wrapper, wrapper.getArgument(), true)).map(response -> ResponseEntity.ok()
				.headers(response.getHeaders()).body((Publisher<?>) response.getBody()));
	}

	@PostMapping(path = "/**")
	@ResponseBody
	public Object post(WebRequest request, @RequestBody(required = false) String body) {
		String argument = StringUtils.hasText(body) ? body : "";
		return FunctionWebRequestProcessingHelper.processRequest(wrapper(request), argument, false);
	}

	@GetMapping(path = "/**")
	@ResponseBody
	public Object get(WebRequest request) {
		FunctionWrapper wrapper = wrapper(request);
		return FunctionWebRequestProcessingHelper.processRequest(wrapper, wrapper.getArgument(), false);
	}

	private FunctionWrapper wrapper(WebRequest request) {
		FunctionInvocationWrapper function = (FunctionInvocationWrapper) request
				.getAttribute(WebRequestConstants.HANDLER, WebRequest.SCOPE_REQUEST);
		FunctionWrapper wrapper = new FunctionWrapper(function);
		for (String key : request.getParameterMap().keySet()) {
			wrapper.getParams().addAll(key, Arrays.asList(request.getParameterValues(key)));
		}
		for (Iterator<String> keys = request.getHeaderNames(); keys.hasNext();) {
			String key = keys.next();
			wrapper.getHeaders().addAll(key, Arrays.asList(request.getHeaderValues(key)));
		}

		HttpHeaders headers = HttpHeaders.writableHttpHeaders(wrapper.getHeaders());
		headers.set("uri", ((ServletWebRequest) request).getRequest().getRequestURI());

		String argument = (String) request.getAttribute(WebRequestConstants.ARGUMENT,
				WebRequest.SCOPE_REQUEST);
		if (argument != null) {
			wrapper.setArgument(argument);
		}
		return wrapper;
	}
}
