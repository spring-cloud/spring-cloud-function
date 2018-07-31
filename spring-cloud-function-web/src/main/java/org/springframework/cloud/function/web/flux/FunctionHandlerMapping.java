/*
 * Copyright 2017-2018 the original author or authors.
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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

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
	public Mono<HandlerMethod> getHandlerInternal(ServerWebExchange request) {
		String path = request.getRequest().getPath().pathWithinApplication().value();
		if (StringUtils.hasText(prefix) && !path.startsWith(prefix)) {
			return Mono.empty();
		}
		Mono<HandlerMethod> handler = super.getHandlerInternal(request);
		if (path == null) {
			return handler;
		}
		if (path.startsWith(prefix)) {
			path = path.substring(prefix.length());
		}
		Object function = findFunctionForGet(request, path);
		if (function == null) {
			function = findFunctionForPost(request, path);
		}
		if (function != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Found function for POST: " + path);
			}
			request.getAttributes().put(WebRequestConstants.HANDLER, function);
		}
		Object actual = function;
		return handler.filter(method -> actual != null);
	}

	private Object findFunctionForPost(ServerWebExchange request, String path) {
		if (!request.getRequest().getMethod().equals(HttpMethod.POST)) {
			return null;
		}
		path = path.startsWith("/") ? path.substring(1) : path;
		Consumer<Publisher<?>> consumer = functions.lookup(Consumer.class, path);
		if (consumer != null) {
			request.getAttributes().put(WebRequestConstants.CONSUMER, consumer);
			return consumer;
		}
		Function<Object, Object> function = functions.lookup(Function.class, path);
		if (function != null) {
			request.getAttributes().put(WebRequestConstants.FUNCTION, function);
			return function;
		}
		return null;
	}

	private Object findFunctionForGet(ServerWebExchange request, String path) {
		if (!request.getRequest().getMethod().equals(HttpMethod.GET)) {
			return null;
		}
		path = path.startsWith("/") ? path.substring(1) : path;

		Object functionForGet = null;
		Supplier<Publisher<?>> supplier = functions.lookup(Supplier.class, path);
		if (supplier != null) {
			request.getAttributes().put(WebRequestConstants.SUPPLIER, supplier);
			functionForGet = supplier;
		}
		else {
			StringBuilder builder = new StringBuilder();
			String name = path;
			String[] splitPath =  path.split("/");
			Function<Object, Object> function = null;
			for (int i = 0; i < splitPath.length || function != null; i++) {
				String element = splitPath[i];
				if (builder.length() > 0) {
					builder.append("/");
				}
				builder.append(element);
				name = builder.toString();

				function = functions.lookup(Function.class, name);
				if (function != null) {
					request.getAttributes().put(WebRequestConstants.FUNCTION, function);
					String value = path.length() > name.length()
							? path.substring(name.length() + 1)
								: null;
					request.getAttributes().put(WebRequestConstants.ARGUMENT, value);
					functionForGet = function;
				}
			}
		}

		return functionForGet;
	}

}
