/*
 * Copyright 2012-2019 the original author or authors.
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


import reactor.core.publisher.Mono;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.cloud.function.web.util.FunctionWebRequestProcessingHelper;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
@Configuration
@ConditionalOnClass(RequestMappingHandlerMapping.class)
public class FunctionHandlerMapping extends RequestMappingHandlerMapping
		implements InitializingBean {

	private final FunctionCatalog functions;

	private final FunctionController controller;

	private final FunctionProperties functionProperties;

	@Value("${spring.cloud.function.web.path:}")
	private String prefix = "";

	@Autowired
	public FunctionHandlerMapping(FunctionCatalog catalog,
			FunctionController controller, FunctionProperties functionProperties) {
		this.functions = catalog;
		this.logger.info("FunctionCatalog: " + catalog);
		setOrder(super.getOrder() - 5);
		this.controller = controller;
		this.functionProperties = functionProperties;
	}

	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		detectHandlerMethods(this.controller);
		while (this.prefix.endsWith("/")) {
			this.prefix = this.prefix.substring(0, this.prefix.length() - 1);
		}
	}

	@Override
	public Mono<HandlerMethod> getHandlerInternal(ServerWebExchange request) {
		String path = request.getRequest().getPath().pathWithinApplication().value();
		if (StringUtils.hasText(this.prefix) && !path.startsWith(this.prefix)) {
			return Mono.empty();
		}
		Mono<HandlerMethod> handler = super.getHandlerInternal(request);
		if (path == null) {
			return handler;
		}
		if (path.startsWith(this.prefix)) {
			path = path.substring(this.prefix.length());
		}
		Object function = FunctionWebRequestProcessingHelper
				.findFunction(this.functionProperties, request.getRequest().getMethod(), this.functions, request.getAttributes(), path, new String[] {});

		if (function != null) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Found function for POST: " + path);
			}
			request.getAttributes().put(WebRequestConstants.HANDLER, function);
		}
		Object actual = function;
		return handler.filter(method -> actual != null);
	}

	@Override
	protected void initHandlerMethods() {
	}
}
