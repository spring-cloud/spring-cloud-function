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

import org.reactivestreams.Publisher;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import reactor.core.publisher.Flux;

/**
 * A specialized {@link ResponseBodyEmitter} that handles {@link Flux} return types with
 * SSE streams.
 *
 * @author Dave Syer
 */
class FluxResponseSseEmitter<T> extends SseEmitter {

	public FluxResponseSseEmitter(Publisher<T> observable) {
		this(MediaType.valueOf("text/event-stream"), observable);
	}

	public FluxResponseSseEmitter(MediaType mediaType, Publisher<T> observable) {
		super();
		new ResponseBodyEmitterSubscriber<>(mediaType, observable, this);
	}

}
