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

package org.springframework.cloud.function.web.mvc;

import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.cloud.function.web.util.FunctionWebRequestProcessingHelper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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
	public FunctionHandlerMapping(FunctionProperties functionProperties, FunctionCatalog catalog,
			FunctionController controller) {
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
		if (path == null) {
			return handler;
		}
		if (StringUtils.hasText(this.prefix) && !path.startsWith(this.prefix)) {
			return null;
		}
		if (path.startsWith(this.prefix)) {
			path = path.substring(this.prefix.length());
		}

		Object function = FunctionWebRequestProcessingHelper.findFunction(this.functionProperties, HttpMethod.resolve(request.getMethod()),
				this.functions, new HttpRequestAttributeDelegate(request), path, new String[] {});
		if (function != null) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Found function for GET: " + path);
			}
			request.setAttribute(WebRequestConstants.HANDLER, function);
			return handler;
		}
		return null;
	}

	@SuppressWarnings("serial")
	private static class HttpRequestAttributeDelegate extends HashMap<String, Object> {
		private final HttpServletRequest request;
		HttpRequestAttributeDelegate(HttpServletRequest request) {
			this.request = request;
		}

		@Override
		public Object put(String key, Object value) {
			this.request.setAttribute(key, value);
			return value;
		}
	}

}
