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

package org.springframework.cloud.function.web.flux.response;

import org.reactivestreams.Publisher;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import reactor.core.publisher.Flux;

/**
 * A specialized {@link ResponseBodyEmitter} that handles {@link Flux} return types with
 * SSE streams.
 *
 * @author Dave Syer
 */
class FluxResponseSseEmitter extends SseEmitter {

	private ResponseBodyEmitterSubscriber subscriber;

	public FluxResponseSseEmitter(Publisher<?> observable) {
		this(new HttpHeaders(), MediaType.valueOf("text/plain"), observable);
	}

	public FluxResponseSseEmitter(HttpHeaders request, MediaType mediaType,
			Publisher<?> observable) {
		super();
		this.subscriber = new ResponseBodyEmitterSubscriber(request, mediaType,
				observable, this, false);
	}

	@Override
	protected void extendResponse(ServerHttpResponse outputMessage) {
		super.extendResponse(outputMessage);
		this.subscriber.extendResponse(outputMessage);
	}
}
