/*
 * Copyright 2012-2015 the original author or authors.
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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.servlet.http.HttpServletRequest;

import org.reactivestreams.Publisher;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
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

	private final FunctionCatalog functions;

	private final FunctionController controller;

	@Value("${spring.cloud.function.web.path:}")
	private String prefix = "";

	@Autowired
	public FunctionHandlerMapping(FunctionCatalog catalog,
			FunctionController controller) {
		this.functions = catalog;
		logger.info("FunctionCatalog: " + catalog);
		setOrder(super.getOrder() - 5);
		this.controller = controller;
	}

	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		detectHandlerMethods(controller);
		while (prefix.endsWith("/")) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
	}

	@Override
	protected void initHandlerMethods() {
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
		if (StringUtils.hasText(prefix) && !path.startsWith(prefix)) {
			return null;
		}
		if (path.startsWith(prefix)) {
			path = path.substring(prefix.length());
		}
		if (path == null) {
			return handler;
		}
		Object function = findFunctionForGet(request, path);
		if (function != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Found function for GET: " + path);
			}
			request.setAttribute(WebRequestConstants.HANDLER, function);
			return handler;
		}
		function = findFunctionForPost(request, path);
		if (function != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Found function for POST: " + path);
			}
			request.setAttribute(WebRequestConstants.HANDLER, function);
			return handler;
		}
		return null;
	}

	private Object findFunctionForPost(HttpServletRequest request, String path) {
		if (!request.getMethod().equals("POST")) {
			return null;
		}
		path = path.startsWith("/") ? path.substring(1) : path;
		Consumer<Publisher<?>> consumer = functions.lookup(Consumer.class, path);
		if (consumer != null) {
			request.setAttribute(WebRequestConstants.CONSUMER, consumer);
			return consumer;
		}
		Function<Object, Object> function = functions.lookup(Function.class, path);
		if (function != null) {
			request.setAttribute(WebRequestConstants.FUNCTION, function);
			return function;
		}
		return null;
	}

	private Object findFunctionForGet(HttpServletRequest request, String path) {
		if (!request.getMethod().equals("GET")) {
			return null;
		}
		path = path.startsWith("/") ? path.substring(1) : path;
		Supplier<Publisher<?>> supplier = functions.lookup(Supplier.class, path);
		if (supplier != null) {
			request.setAttribute(WebRequestConstants.SUPPLIER, supplier);
			return supplier;
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
			Function<Object, Object> function = functions.lookup(Function.class, name);
			if (function != null) {
				request.setAttribute(WebRequestConstants.FUNCTION, function);
				request.setAttribute(WebRequestConstants.ARGUMENT, value);
				return function;
			}
		}
		return null;
	}

}
