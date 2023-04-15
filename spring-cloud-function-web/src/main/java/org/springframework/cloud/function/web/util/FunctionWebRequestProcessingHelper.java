/*
 * Copyright 2019-2021 the original author or authors.
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

package org.springframework.cloud.function.web.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;



/**
 * !INTERNAL USE ONLY!
 *
 * @author Oleg Zhurakousky
 *
 */
public final class FunctionWebRequestProcessingHelper {

	private static Log logger = LogFactory.getLog(FunctionWebRequestProcessingHelper.class);

	private FunctionWebRequestProcessingHelper() {

	}

	public static FunctionInvocationWrapper findFunction(FunctionProperties functionProperties, HttpMethod method, FunctionCatalog functionCatalog,
											Map<String, Object> attributes, String path) {
		if (method.equals(HttpMethod.GET) || method.equals(HttpMethod.POST)) {
			return doFindFunction(functionProperties.getDefinition(), method, functionCatalog, attributes, path);
		}
		else {
			throw new IllegalStateException("HTTP method '" + method + "' is not supported;");
		}
	}

	public static Object invokeFunction(FunctionInvocationWrapper function, Object input, boolean isMessage) {
		Object result = function.apply(input);
		return postProcessResult(result, isMessage);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Publisher<?> processRequest(FunctionWrapper wrapper, Object argument, boolean eventStream) {
		if (argument == null) {
			argument = "";
		}
		FunctionInvocationWrapper function = wrapper.getFunction();

		if (function == null) {
			return Mono.just(ResponseEntity.notFound().build());
		}

		HttpHeaders headers = wrapper.getHeaders();

		Message<?> inputMessage = null;


		MessageBuilder builder = MessageBuilder.withPayload(argument);
		if (!CollectionUtils.isEmpty(wrapper.getParams())) {
			builder = builder.setHeader(HeaderUtils.HTTP_REQUEST_PARAM, wrapper.getParams().toSingleValueMap());
		}
		inputMessage = builder.copyHeaders(headers.toSingleValueMap()).build();

		if (function.isRoutingFunction()) {
			function.setSkipOutputConversion(true);
		}

		Object result = function.apply(inputMessage);
		if (function.isConsumer()) {
			if (result instanceof Publisher) {
				Mono.from((Publisher) result).subscribe();
			}
			return Mono.just(ResponseEntity.accepted().headers(HeaderUtils.sanitize(headers)).build());
		}

		BodyBuilder responseOkBuilder = ResponseEntity.ok().headers(HeaderUtils.sanitize(headers));

		Publisher pResult;
		if (result instanceof Publisher) {
			pResult = (Publisher) result;
			if (eventStream) {
				return Flux.from(pResult);
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
			if (v instanceof Iterable i) {
				List aggregatedResult = (List) StreamSupport.stream(i.spliterator(), false).map(m -> {
					return m instanceof Message ? processMessage(responseOkBuilder, (Message<?>) m) : m;
				}).collect(Collectors.toList());
				return responseOkBuilder.header("content-type", "application/json").body(aggregatedResult);
			}
			else if (v instanceof Message) {
				return responseOkBuilder.body(processMessage(responseOkBuilder, (Message<?>) v));
			}
			else {
				return responseOkBuilder.body(v);
			}
		});
	}

	private static Object processMessage(BodyBuilder responseOkBuilder, Message<?> message) {
		responseOkBuilder.headers(HeaderUtils.fromMessage(message.getHeaders()));
		return message.getPayload();
	}

	private static FunctionInvocationWrapper doFindFunction(String functionDefinition, HttpMethod method, FunctionCatalog functionCatalog,
											Map<String, Object> attributes, String path) {

		path = path.startsWith("/") ? path.substring(1) : path;
		if (method.equals(HttpMethod.GET)) {
			FunctionInvocationWrapper function = functionCatalog.lookup(path);
			if (function != null && function.isSupplier()) {
				attributes.put(WebRequestConstants.SUPPLIER, function);
				return function;
			}
		}

		StringBuilder builder = new StringBuilder();
		String name = path;
		String value = null;
		for (String element : path.split("/")) {
			if (builder.length() > 0) {
				builder.append("/");
			}
			builder.append(element);
			name = builder.toString();
			value = path.length() > name.length() ? path.substring(name.length() + 1)
					: null;
			FunctionInvocationWrapper function = functionCatalog.lookup(name);
			if (function != null) {
				return postProcessFunction(function, value, attributes);
			}
		}

		if (StringUtils.hasText(functionDefinition)) {
			FunctionInvocationWrapper function = functionCatalog.lookup(functionDefinition);
			if (function != null) {
				return postProcessFunction(function, value, attributes);
			}
		}
		return null;
	}

	private static FunctionInvocationWrapper postProcessFunction(FunctionInvocationWrapper function, String argument,  Map<String, Object> attributes) {
		attributes.put(WebRequestConstants.FUNCTION, function);
		if (argument != null) {
			attributes.put(WebRequestConstants.ARGUMENT, argument);
		}
		return function;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Object postProcessResult(Object result, boolean isMessage) {
		if (result instanceof Flux) {
			result = ((Flux) result).map(v -> postProcessResult(v, isMessage));
		}
		else if (result instanceof Mono) {
			result = ((Mono) result).map(v -> postProcessResult(v, isMessage));
		}
		else if (result instanceof Message) {
			if (((Message) result).getPayload() instanceof byte[]) {
				String str = new String((byte[]) ((Message) result).getPayload());
				result = MessageBuilder.withPayload(str).copyHeaders(((Message) result).getHeaders()).build();
			}
		}

		if (result instanceof byte[]) {
			result = new String((byte[]) result);
		}
		return result;
	}

}
