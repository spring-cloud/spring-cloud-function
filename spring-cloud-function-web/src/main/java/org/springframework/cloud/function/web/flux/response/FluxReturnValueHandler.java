/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.function.web.flux.response;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.el.stream.Optional;
import org.reactivestreams.Publisher;

import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.function.web.flux.constants.WebRequestConstants;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitterReturnValueHandler;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A specialized {@link AsyncHandlerMethodReturnValueHandler} that handles {@link Flux}
 * return types.
 *
 * @author Dave Syer
 */
public class FluxReturnValueHandler implements AsyncHandlerMethodReturnValueHandler {

	private static Log logger = LogFactory.getLog(FluxReturnValueHandler.class);

	private ResponseBodyEmitterReturnValueHandler delegate;
	private RequestResponseBodyMethodProcessor single;
	private long timeout = 1000L;
	private static final MediaType EVENT_STREAM = MediaType.valueOf("text/event-stream");

	private FunctionInspector inspector;

	private MethodParameter singleReturnType;

	public FluxReturnValueHandler(FunctionInspector inspector,
			List<HttpMessageConverter<?>> messageConverters) {
		this.inspector = inspector;
		this.delegate = new ResponseBodyEmitterReturnValueHandler(messageConverters);
		this.single = new RequestResponseBodyMethodProcessor(messageConverters);
		Method method = ReflectionUtils.findMethod(getClass(), "singleValue");
		singleReturnType = new MethodParameter(method, -1);
	}

	ResponseEntity<Object> singleValue() {
		return null;
	}

	/**
	 * Timeout for clients. If no items are seen on an HTTP response in this period then
	 * the response is closed.
	 * 
	 * @param timeout the timeout to set
	 */
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	@Override
	public boolean isAsyncReturnValue(Object returnValue, MethodParameter returnType) {
		if (returnValue != null) {
			return supportsReturnType(returnType);
		}
		return false;
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return (returnType.getParameterType() != null
				&& (Publisher.class.isAssignableFrom(returnType.getParameterType())
						|| isResponseEntity(returnType)))
				|| Publisher.class
						.isAssignableFrom(returnType.getMethod().getReturnType());
	}

	private boolean isResponseEntity(MethodParameter returnType) {
		if (ResponseEntity.class.isAssignableFrom(returnType.getParameterType())) {
			Class<?> bodyType = ResolvableType.forMethodParameter(returnType)
					.getGeneric(0).resolve();
			return bodyType != null && Flux.class.isAssignableFrom(bodyType);
		}
		return false;
	}

	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest)
			throws Exception {

		if (returnValue == null) {
			mavContainer.setRequestHandled(true);
			return;
		}

		Object adaptFrom = returnValue;
		if (returnValue instanceof ResponseEntity) {
			ResponseEntity<?> value = (ResponseEntity<?>) returnValue;
			adaptFrom = value.getBody();
			webRequest.getNativeResponse(HttpServletResponse.class)
					.setStatus(value.getStatusCodeValue());
		}
		Publisher<?> flux = (Publisher<?>) adaptFrom;

		Object handler = webRequest.getAttribute(WebRequestConstants.HANDLER,
				NativeWebRequest.SCOPE_REQUEST);
		Class<?> type = inspector.getOutputType(inspector.getName(handler));

		boolean inputSingle = isInputSingle(webRequest, handler);
		if (inputSingle && isOutputSingle(handler)) {
			single.handleReturnValue(Flux.from(flux).blockFirst(), singleReturnType,
					mavContainer, webRequest);
			return;
		}

		MediaType mediaType = null;
		if (isPlainText(webRequest) && CharSequence.class.isAssignableFrom(type)) {
			mediaType = MediaType.TEXT_PLAIN;
		}
		else {
			mediaType = findMediaType(webRequest);
		}
		if (logger.isDebugEnabled()) {
			logger.debug(
					"Handling return value " + type + " with media type: " + mediaType);
		}
		delegate.handleReturnValue(getEmitter(timeout, flux, mediaType), returnType,
				mavContainer, webRequest);
	}

	private boolean isInputSingle(NativeWebRequest webRequest, Object handler) {
		Boolean single = (Boolean) webRequest.getAttribute(
				WebRequestConstants.INPUT_SINGLE, NativeWebRequest.SCOPE_REQUEST);
		if (single == null) {
			return handler instanceof Supplier;
		}
		return single;
	}

	private boolean isOutputSingle(Object handler) {
		String name = inspector.getName(handler);
		Class<?> type = inspector.getOutputType(name);
		Class<?> wrapper = inspector.getOutputWrapper(name);
		if (Stream.class.isAssignableFrom(type)) {
			return false;
		}
		if (wrapper == type) {
			return true;
		}
		if (Mono.class.equals(wrapper) || Optional.class.equals(wrapper)) {
			return true;
		}
		return false;
	}

	private MediaType findMediaType(NativeWebRequest webRequest) {
		List<MediaType> accepts = Arrays.asList(MediaType.ALL);
		MediaType mediaType = null;
		if (webRequest.getHeader("Accept") != null) {
			accepts = MediaType.parseMediaTypes(webRequest.getHeader("Accept"));
			for (MediaType accept : accepts) {
				if (!MediaType.ALL.equals(accept)
						&& MediaType.APPLICATION_JSON.isCompatibleWith(accept)) {
					mediaType = MediaType.APPLICATION_JSON;
					// Prefer JSON if that is acceptable
					break;
				}
				else if (mediaType == null) {
					mediaType = accept;
				}
			}
		}
		if (mediaType == null) {
			mediaType = MediaType.APPLICATION_JSON;
		}
		return mediaType;
	}

	private boolean isPlainText(NativeWebRequest webRequest) {
		String value = webRequest.getHeader("Content-Type");
		if (value != null) {
			return MediaType.valueOf(value).isCompatibleWith(MediaType.TEXT_PLAIN);
		}
		return false;
	}

	private ResponseBodyEmitter getEmitter(Long timeout, Publisher<?> flux,
			MediaType mediaType) {
		Publisher<?> exported = flux instanceof Mono ? Mono.from(flux)
				: Flux.from(flux).timeout(Duration.ofMillis(timeout), Flux.empty());
		if (!MediaType.ALL.equals(mediaType)
				&& EVENT_STREAM.isCompatibleWith(mediaType)) {
			// TODO: more subtle content negotiation
			return new FluxResponseSseEmitter<>(MediaType.APPLICATION_JSON, exported);
		}
		return new FluxResponseBodyEmitter<>(mediaType, exported);
	}

}
