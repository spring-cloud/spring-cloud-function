/*
 * Copyright 2013-2016 the original author or authors.
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

import java.time.Duration;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.reactivestreams.Publisher;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
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

	private ResponseBodyEmitterReturnValueHandler delegate;
	private long timeout = 1000L;
	private static final MediaType EVENT_STREAM = MediaType.valueOf("text/event-stream");

	public FluxReturnValueHandler(List<HttpMessageConverter<?>> messageConverters) {
		delegate = new ResponseBodyEmitterReturnValueHandler(messageConverters);
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
		return returnValue != null && supportsReturnType(returnType);
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return Publisher.class.isAssignableFrom(returnType.getParameterType())
				|| isResponseEntity(returnType);
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
		Object adaptFrom = returnValue;
		if (returnValue instanceof ResponseEntity) {
			ResponseEntity<?> value = (ResponseEntity<?>) returnValue;
			adaptFrom = value.getBody();
			webRequest.getNativeResponse(HttpServletResponse.class)
					.setStatus(value.getStatusCodeValue());
		}
		Publisher<?> flux = (Publisher<?>) adaptFrom;

		MediaType mediaType = webRequest.getHeader("Accept") == null ? null
				: MediaType.parseMediaTypes(webRequest.getHeader("Accept")).iterator()
						.next();
		delegate.handleReturnValue(getEmitter(timeout, flux, mediaType), returnType,
				mavContainer, webRequest);
	}

	private ResponseBodyEmitter getEmitter(Long timeout, Publisher<?> flux,
			MediaType mediaType) {
		Publisher<?> exported = flux instanceof Mono ? Mono.from(flux)
				: Flux.from(flux).timeout(Duration.ofMillis(timeout), Flux.empty());
		if (!MediaType.ALL.equals(mediaType)
				&& EVENT_STREAM.isCompatibleWith(mediaType)) {
			return new FluxResponseSseEmitter<>(mediaType, exported);
		}
		return new FluxResponseBodyEmitter<>(mediaType, exported);
	}

}
