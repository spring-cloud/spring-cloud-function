/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.function.web.util;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.http.HttpMethod;


public final class FunctionWebUtils {

	private FunctionWebUtils() {

	}

	public static Object findFunction(HttpMethod method, FunctionCatalog functionCatalog,
											Map<String, Object> attributes, String path) {
		if (method.equals(HttpMethod.GET)) {
			return findFunctionForGet(functionCatalog, attributes, path);
		}
		else if (method.equals(HttpMethod.POST)) {
			return findFunctionForPost(functionCatalog, attributes, path);
		}
		else {
			throw new IllegalStateException("HTTP method '" + method + "' is not supported;");
		}
	}

	private static Object findFunctionForGet(FunctionCatalog functionCatalog,
											Map<String, Object> attributes, String path) {
		path = path.startsWith("/") ? path.substring(1) : path;

		Object functionForGet = null;
		Supplier<Publisher<?>> supplier = functionCatalog.lookup(Supplier.class, path);
		if (supplier != null) {
			attributes.put(WebRequestConstants.SUPPLIER, supplier);
			functionForGet = supplier;
		}
		else {
			StringBuilder builder = new StringBuilder();
			String name = path;
			String[] splitPath = path.split("/");
			Function<Object, Object> function = null;
			for (int i = 0; i < splitPath.length || function != null; i++) {
				String element = splitPath[i];
				if (builder.length() > 0) {
					builder.append("/");
				}
				builder.append(element);
				name = builder.toString();

				function = functionCatalog.lookup(Function.class, name);
				if (function != null) {
					attributes.put(WebRequestConstants.FUNCTION, function);
					String value = path.length() > name.length()
							? path.substring(name.length() + 1) : null;
							attributes.put(WebRequestConstants.ARGUMENT, value);
					functionForGet = function;
				}
			}
		}

		return functionForGet;
	}

	private static Object findFunctionForPost(FunctionCatalog functionCatalog,
											Map<String, Object> attributes, String path) {
		path = path.startsWith("/") ? path.substring(1) : path;
		Consumer<Publisher<?>> consumer = functionCatalog.lookup(Consumer.class, path);
		if (consumer != null) {
			attributes.put(WebRequestConstants.CONSUMER, consumer);
			return consumer;
		}
		Function<Object, Object> function = functionCatalog.lookup(Function.class, path);
		if (function != null) {
			attributes.put(WebRequestConstants.FUNCTION, function);
			return function;
		}
		return null;
	}
}
