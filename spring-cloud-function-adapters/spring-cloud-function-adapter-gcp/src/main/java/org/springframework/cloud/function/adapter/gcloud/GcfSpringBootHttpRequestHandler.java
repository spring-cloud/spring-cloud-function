/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.function.adapter.gcloud;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;

/**
 * Implementation of {@link HttpFunction} for Google Cloud Function (GCF).
 * This is the Spring Cloud Function adapter for GCF HTTP function.
 *
 * @author Dmitry Solomakha
 * @author Mike Eltsufin
 */
public class GcfSpringBootHttpRequestHandler
	extends AbstractSpringFunctionAdapterInitializer<HttpRequest> implements HttpFunction {

	private final Gson gson = new Gson();

	public GcfSpringBootHttpRequestHandler() {
		super();
	}

	public GcfSpringBootHttpRequestHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	/**
	 * The implementation of a GCF {@link HttpFunction} that will be used as the entrypoint from GCF.
	 */
	@Override
	public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
		Thread.currentThread()
			.setContextClassLoader(GcfSpringBootHttpRequestHandler.class.getClassLoader());

		initialize(httpRequest);

		Publisher<?> input;
		if (getInputType() == Void.class) {
			input = Mono.empty();
		}
		else {
			input = Mono.just(gson.fromJson(httpRequest.getReader(), getInputType()));
		}

		Publisher<?> output = this.apply(input);

		Object result = this.result(input, output);

		httpResponse.getWriter().write(gson.toJson(result));
		httpResponse.getWriter().close();
	}
}
