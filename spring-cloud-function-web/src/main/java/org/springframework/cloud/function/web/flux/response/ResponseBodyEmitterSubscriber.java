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

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Subscriber that emits any value produced by the {@link Flux} into the delegated
 * {@link ResponseBodyEmitter}.
 *
 * @author Dave Syer
 */
class ResponseBodyEmitterSubscriber<T> implements Subscriber<T> {

	private final MediaType mediaType;

	private Subscription subscription;

	private final ResponseBodyEmitter responseBodyEmitter;

	private boolean completed;

	private boolean firstElementWritten;

	private boolean single;

	private boolean json;

	public ResponseBodyEmitterSubscriber(MediaType mediaType, Publisher<T> observable,
			ResponseBodyEmitter responseBodyEmitter, boolean json) {

		this.mediaType = mediaType;
		this.responseBodyEmitter = responseBodyEmitter;
		this.json = json;
		this.responseBodyEmitter.onTimeout(new Timeout());
		this.responseBodyEmitter.onCompletion(new Complete());
		this.single = observable instanceof Mono;
		observable.subscribe(this);
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		this.subscription = subscription;
		subscription.request(Long.MAX_VALUE);
	}

	@Override
	public void onNext(T value) {

		Object object = value;

		try {
			if (isJson()) {
				if (!this.firstElementWritten) {
					if (!single) {
						responseBodyEmitter.send("[");
						this.firstElementWritten = true;
					}
				}
				else {
					responseBodyEmitter.send(",");
				}
				if (!single && value.getClass() == String.class
						&& !((String) value).contains("\"")) {
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
		if (!completed) {
			completed = true;
			try {
				if (isJson()) {
					if (!single) {
						if (!this.firstElementWritten) {
							responseBodyEmitter.send("[]");
						}
						else {
							responseBodyEmitter.send("]");
						}
					}
				}
				if (e instanceof TimeoutException) {
					responseBodyEmitter.complete();
				}
				else {
					responseBodyEmitter.completeWithError(e);
				}
			}
			catch (IOException ex) {
				throw new RuntimeException(ex.getMessage(), ex);
			}
		}
	}

	@Override
	public void onComplete() {
		if (!completed) {
			completed = true;
			try {
				if (isJson()) {
					if (!single) {
						if (!this.firstElementWritten) {
							responseBodyEmitter.send("[");
						}
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

	private boolean isJson() {
		return json;
	}

	class Complete implements Runnable {

		@Override
		public void run() {
			ResponseBodyEmitterSubscriber.this.subscription.cancel();
		}
	}

	class Timeout implements Runnable {

		@Override
		public void run() {
			onComplete();
			ResponseBodyEmitterSubscriber.this.subscription.cancel();
		}
	}
}
