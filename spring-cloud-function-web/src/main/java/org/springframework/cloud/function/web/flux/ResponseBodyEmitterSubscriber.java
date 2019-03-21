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

package org.springframework.cloud.function.web.flux;

import java.io.IOException;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import reactor.core.publisher.Flux;

/**
 * Subscriber that emits any value produced by the {@link Flux} into the delegated
 * {@link ResponseBodyEmitter}.
 *
 * @author Dave Syer
 */
class ResponseBodyEmitterSubscriber<T> implements Subscriber<T>, Runnable {

	private final MediaType mediaType;

	private Subscription subscription;

	private final ResponseBodyEmitter responseBodyEmitter;

	private boolean completed;

	private boolean firstElementWritten;

	public ResponseBodyEmitterSubscriber(MediaType mediaType, Flux<T> observable,
			ResponseBodyEmitter responseBodyEmitter) {

		this.mediaType = mediaType;
		this.responseBodyEmitter = responseBodyEmitter;
		this.responseBodyEmitter.onTimeout(this);
		this.responseBodyEmitter.onCompletion(this);
		observable.subscribe(this);
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		if (!MediaType.ALL.equals(mediaType) && MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
			try {
				this.responseBodyEmitter.send("[");
			}
			catch (IOException e) {
				// Urgh?
			}
		}
		this.subscription = subscription;
		subscription.request(Long.MAX_VALUE);
	}

	@Override
	public void onNext(T value) {
		
		Object object = value;

		try {
			if (!MediaType.ALL.equals(mediaType) && MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
				if (!this.firstElementWritten) {
					this.firstElementWritten = true;
				}
				else {
					responseBodyEmitter.send(",");
				}
				if (value.getClass()==String.class && !((String)value).contains("\"")) {
					object = "\"" + value + "\"";
				}
			}
			if (!completed) {
				responseBodyEmitter.send(object, mediaType);
			}
		}
		catch (

		IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	@Override
	public void onError(Throwable e) {
		responseBodyEmitter.completeWithError(e);
	}

	@Override
	public void onComplete() {
		if (!completed) {
			completed = true;
			try {
				if (!MediaType.ALL.equals(mediaType) && MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
					if (!this.firstElementWritten) {

						this.firstElementWritten = true;
					}
					else {
						responseBodyEmitter.send("]");
					}
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e.getMessage(), e);
			}
			responseBodyEmitter.complete();
		}
	}

	@Override
	public void run() {
		this.subscription.cancel();
	}
}
