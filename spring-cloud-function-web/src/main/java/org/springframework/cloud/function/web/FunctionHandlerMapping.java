/*
 * Copyright 2012-2015 the original author or authors.
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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ConditionalOnClass(RequestMappingHandlerMapping.class)
public class FunctionHandlerMapping extends RequestMappingHandlerMapping
		implements InitializingBean {

	public static final String FUNCTION = FunctionHandlerMapping.class.getName()
			+ ".function";
	public static final String CONSUMER = FunctionHandlerMapping.class.getName()
			+ ".consumer";
	public static final String SUPPLIER = FunctionHandlerMapping.class.getName()
			+ ".supplier";
	public static final String ARGUMENT = FunctionHandlerMapping.class.getName()
			+ ".argument";
	private final FunctionCatalog functions;

	private final FunctionController controller;
	
	@Value("${spring.cloud.function.web.path:}")
	private String prefix = ""; 

	@Autowired
	public FunctionHandlerMapping(FunctionCatalog catalog) {
		this.functions = catalog;
		setOrder(super.getOrder() - 5);
		this.controller = new FunctionController();
	}

	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		detectHandlerMethods(controller);
		while (prefix.endsWith("/")) {
			prefix = prefix.substring(0, prefix.length()-1);
		}
	}

	@Override
	protected HandlerMethod getHandlerInternal(HttpServletRequest request)
			throws Exception {
		HandlerMethod handler = super.getHandlerInternal(request);
		if (handler == null) {
			return null;
		}
		String path = (String) request
				.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
		if (path == null) {
			return handler;
		}
		if (findFunctionForGet(request, path) != null) {
			return handler;
		}
		if (findFunctionForPost(request, path) != null) {
			return handler;
		}
		return null;
	}

	private Object findFunctionForPost(HttpServletRequest request, String path) {
		if (!request.getMethod().equals("POST")) {
			return null;
		}
		path = path.startsWith("/") ? path.substring(1) : path;
		Function<Object, Object> function = functions.lookupFunction(path);
		if (function != null) {
			request.setAttribute(FUNCTION, function);
			return function;
		}
		Consumer<Object> consumer = functions.lookupConsumer(path);
		if (consumer != null) {
			request.setAttribute(CONSUMER, consumer);
			return consumer;
		}
		return null;
	}

	private Object findFunctionForGet(HttpServletRequest request, String path) {
		if (!request.getMethod().equals("GET")) {
			return null;
		}
		path = path.startsWith("/") ? path.substring(1) : path;
		StringBuilder builder = new StringBuilder();
		String name = path;
		String value = null;
		for (String element : path.split("/")) {
			if (builder.length()>0) {
				builder.append("/");
			}
			builder.append(element);
			name = builder.toString();
			value = path.length() > name.length() ? path.substring(name.length() + 1)
					: null;
			Function<Object, Object> function = functions.lookupFunction(name);
			if (function != null) {
				request.setAttribute(FUNCTION, function);
				request.setAttribute(ARGUMENT, value);
				return function;
			}
		}
		Supplier<Object> supplier = functions.lookupSupplier(path);
		if (supplier != null) {
			request.setAttribute(SUPPLIER, supplier);
			return supplier;
		}
		return null;
	}

}
