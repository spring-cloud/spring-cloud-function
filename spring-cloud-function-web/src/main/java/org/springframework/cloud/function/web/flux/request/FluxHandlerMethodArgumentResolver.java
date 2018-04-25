/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.function.web.flux.request;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.function.web.flux.constants.WebRequestConstants;
import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.cloud.function.web.util.JsonMapper;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Converter for request bodies of type <code>Flux<String></code>.
 * 
 * @author Dave Syer
 *
 */
public class FluxHandlerMethodArgumentResolver
		implements HandlerMethodArgumentResolver, Ordered {

	private static Log logger = LogFactory
			.getLog(FluxHandlerMethodArgumentResolver.class);

	private final JsonMapper mapper;

	private FunctionInspector inspector;

	public FluxHandlerMethodArgumentResolver(FunctionInspector inspector,
			JsonMapper mapper) {
		this.inspector = inspector;
		this.mapper = mapper;
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
			WebDataBinderFactory binderFactory) throws Exception {
		Object handler = webRequest.getAttribute(WebRequestConstants.HANDLER,
				NativeWebRequest.SCOPE_REQUEST);
		Class<?> type = inspector.getInputType(handler);
		if (type == null) {
			type = Object.class;
		}
		boolean message = inspector.isMessage(handler);
		List<?> body;
		ContentCachingRequestWrapper nativeRequest = new ContentCachingRequestWrapper(
				webRequest.getNativeRequest(HttpServletRequest.class));
		if (logger.isDebugEnabled()) {
			logger.debug("Resolving request body into type: " + type);
		}
		if (isPlainText(webRequest) && CharSequence.class.isAssignableFrom(type)) {
			body = Arrays.asList(StreamUtils.copyToString(nativeRequest.getInputStream(),
					Charset.forName("UTF-8")));
		}
		else {
			String json = new String(StreamUtils.copyToString(
					nativeRequest.getInputStream(), Charset.forName("UTF-8")));
			if (!StringUtils.hasText(json)) {
				body = null;
			}
			else {
				if (json.startsWith("[")) {
					body = mapper.toList(json, type);
				}
				else {
					nativeRequest.setAttribute(WebRequestConstants.INPUT_SINGLE, true);
					body = Arrays.asList(mapper.toSingle(json, type));
				}
			}
		}
		if (body != null && message) {
			List<Object> messages = new ArrayList<>();
			MessageHeaders headers = HeaderUtils.fromHttp(new ServletServerHttpRequest(
					webRequest.getNativeRequest(HttpServletRequest.class)).getHeaders());
			for (Object payload : body) {
				messages.add(MessageUtils.create(handler, payload, headers));
			}
			body = messages;
		}
		return new FluxRequest<>(body);
	}

	private boolean isPlainText(NativeWebRequest webRequest) {
		String value = webRequest.getHeader("Content-Type");
		if (value != null) {
			return MediaType.valueOf(value).isCompatibleWith(MediaType.TEXT_PLAIN);
		}
		return false;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return FluxRequest.class.isAssignableFrom(parameter.getParameterType());
	}

}
