/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.function.web.flux.response;

import org.reactivestreams.Publisher;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import reactor.core.publisher.Flux;

/**
 * A specialized {@link ResponseBodyEmitter} that handles {@link Flux} return types.
 *
 * @author Dave Syer
 */
class FluxResponseBodyEmitter<T> extends ResponseBodyEmitter {

	private final MediaType mediaType;

	public FluxResponseBodyEmitter(Publisher<T> observable) {
		this(null, observable);
	}

	public FluxResponseBodyEmitter(MediaType mediaType, Publisher<T> observable) {
		super();
		this.mediaType = mediaType;
		new ResponseBodyEmitterSubscriber<>(mediaType, observable, this,
				MediaType.APPLICATION_JSON.isCompatibleWith(mediaType));
	}

	@Override
	protected void extendResponse(ServerHttpResponse outputMessage) {
		super.extendResponse(outputMessage);

		HttpHeaders headers = outputMessage.getHeaders();
		if (headers.getContentType() == null && this.mediaType != null
				&& !MediaType.ALL.equals(this.mediaType)) {
			headers.setContentType(this.mediaType);
		}
	}
}
