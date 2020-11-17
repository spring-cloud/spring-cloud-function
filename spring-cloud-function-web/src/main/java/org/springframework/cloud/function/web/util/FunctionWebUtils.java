/*
 * Copyright 2019-2020 the original author or authors.
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


import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author Oleg Zhurakousky
 *
 */
public final class FunctionWebUtils {

	private FunctionWebUtils() {

	}

	public static FunctionInvocationWrapper findFunction(HttpMethod method, FunctionCatalog functionCatalog,
											Map<String, Object> attributes, String path, String[] acceptContentTypes) {
		if (method.equals(HttpMethod.GET) || method.equals(HttpMethod.POST)) {
			return doFindFunction(method, functionCatalog, attributes, path, acceptContentTypes);
		}
		else {
			throw new IllegalStateException("HTTP method '" + method + "' is not supported;");
		}
	}

	public static String[] acceptContentTypes(List<MediaType> acceptHeaders) {
		String[] acceptContentTypes = new String[] {};
		if (!CollectionUtils.isEmpty(acceptHeaders)) {
			acceptContentTypes = acceptHeaders.stream().map(mediaType -> mediaType.toString()).toArray(String[]::new);
		}
		else {
			acceptContentTypes = new String[] {MediaType.APPLICATION_JSON.toString()};
		}

		acceptContentTypes = new String[] {StringUtils.arrayToCommaDelimitedString(acceptContentTypes)};
		return new String[] {};
	}

	private static FunctionInvocationWrapper doFindFunction(HttpMethod method, FunctionCatalog functionCatalog,
											Map<String, Object> attributes, String path, String[] acceptContentTypes) {
		path = path.startsWith("/") ? path.substring(1) : path;
		if (method.equals(HttpMethod.GET)) {
			FunctionInvocationWrapper supplier = functionCatalog.lookup(path, acceptContentTypes);
			if (supplier != null) {
				attributes.put(WebRequestConstants.SUPPLIER, supplier);
				return supplier;
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
			FunctionInvocationWrapper function = functionCatalog.lookup(name, acceptContentTypes);
			if (function != null) {
				attributes.put(WebRequestConstants.FUNCTION, function);
				if (value != null) {
					attributes.put(WebRequestConstants.ARGUMENT, value);
				}
				return function;
			}
		}
		return null;
	}

	public static Object invokeFunction(FunctionInvocationWrapper function, Object input, boolean isMessage) {
		Object result = function.apply(input);
		return postProcessResult(result, isMessage);
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
