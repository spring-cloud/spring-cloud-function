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

package org.springframework.cloud.function.adapter.gcloud;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.cloud.functions.Context;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

/**
 * Implementation of {@link HttpFunction} for Google Cloud Function (GCF).
 * This is the Spring Cloud Function adapter for GCF HTTP function.
 *
 * @param <O> input type
 * @author Dmitry Solomakha
 * @author Mike Eltsufin
 */
public class GcfSpringBootHttpRequestHandler<O>
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

		Publisher<?> output = apply(extract(convert(httpRequest)));
		BufferedWriter writer = httpResponse.getWriter();
		Object result = result(httpRequest, output, httpResponse);
		if (returnsOutput()) {
			writer.write(gson.toJson(result));
			writer.flush();
		}
		httpResponse.setStatusCode(200);
	}

	protected Object convert(HttpRequest event) throws IOException {
		Object input = gson.fromJson(event.getReader(), getInputType());

		if (input == null) {
			input = event;
		}

		if (functionAcceptsMessage()) {
			return new GenericMessage<>(input, getHeaders(event));
		}
		else {
			return input;
		}

	}

	private MessageHeaders getHeaders(HttpRequest event) {
		Map<String, Object> headers = new HashMap<String, Object>();

		if (event.getHeaders() != null) {
			headers.putAll(event.getHeaders());
		}
		if (event.getQueryParameters() != null) {
			headers.putAll(event.getQueryParameters());
		}
		if (event.getUri() != null) {
			headers.put("path", event.getPath());
		}

		if (event.getMethod() != null) {
			headers.put("httpMethod", event.getMethod());
		}
		return new MessageHeaders(headers);
	}

	protected boolean functionAcceptsMessage() {
		return this.getInspector().isMessage(function());
	}

	@SuppressWarnings("unchecked")
	protected <T> T result(Object input, Publisher<?> output, HttpResponse resp) {
		List<T> result = new ArrayList<>();
		for (Object value : Flux.from(output).toIterable()) {
			result.add((T) convertOutputAndHeaders(value, resp));
		}
		if (isSingleValue(input) && result.size() == 1) {
			return result.get(0);
		}
		return (T) result;
	}

	private boolean isSingleValue(Object input) {
		return !(input instanceof Collection);
	}

	private Flux<?> extract(Object input) {
		if (input instanceof Collection) {
			return Flux.fromIterable((Iterable<?>) input);
		}
		return Flux.just(input);
	}

	protected O convertOutputAndHeaders(Object output, HttpResponse resp) {
		if (output instanceof Message) {
			Message<?> message = (Message<?>) output;
			for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
				Object values = entry.getValue();
				if (values instanceof List) {
					for (Object value : (List) values) {
						if (value != null) {
							resp.appendHeader(entry.getKey(), value.toString());
						}
					}
				}
				else if (values != null) {
					resp.appendHeader(entry.getKey(), values.toString());
				}
			}
			return (O) message.getPayload();
		}
		else {
			return (O) output;
		}
	}

	private boolean returnsOutput() {
		return !this.getInspector().getOutputType(function()).equals(Void.class);
	}

}
