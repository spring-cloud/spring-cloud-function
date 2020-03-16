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
 * @author Dmitry Solomakha
 */
public class GcloudSpringBootHttpRequestHandler<O>
	extends AbstractSpringFunctionAdapterInitializer<Context> implements HttpFunction {

	Gson gson = new Gson();

	public GcloudSpringBootHttpRequestHandler() {
		super();
	}

	public GcloudSpringBootHttpRequestHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	protected Object convert(HttpRequest event) throws IOException {
		BufferedReader br = event.getReader();
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = br.readLine()) != null) {
			sb.append(line).append("\n");
		}

		String requestBody = sb.toString();
		if (functionAcceptsMessage()) {
			return new GenericMessage<>(toOtionalIfEmpty(requestBody), getHeaders(event, requestBody));
		}
		else {
			return toOtionalIfEmpty(requestBody);
		}

	}

	private Object toOtionalIfEmpty(String requestBody) {
		return requestBody.isEmpty() ? Optional.empty() : requestBody;
	}

	private MessageHeaders getHeaders(HttpRequest event, String requestBody) {
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

		headers.put("request", requestBody);
		return new MessageHeaders(headers);
	}

	protected boolean functionAcceptsMessage() {
		return this.getInspector().isMessage(function());
	}

	@SuppressWarnings("unchecked")
	protected <T> T result(Object input, Publisher<?> output, HttpResponse resp) {
		List<T> result = new ArrayList<>();
		for (Object value : Flux.from(output).toIterable()) {
			result.add((T) convertOutput1(value, resp));
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

	public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
		Thread.currentThread()
			.setContextClassLoader(GcloudSpringBootHttpRequestHandler.class.getClassLoader());
		initialize(new TestExecutionContext());

		Publisher<?> output = apply(extract(convert(httpRequest)));
		BufferedWriter writer = httpResponse.getWriter();
		Object result = result(httpRequest, output, httpResponse);
		if (returnsOutput()) {
			writer.write(gson.toJson(result));
			writer.flush();
		}
		httpResponse.setStatusCode(200);
	}

	protected O convertOutput1(Object output, HttpResponse resp) {
		if (functionReturnsMessage(output)) {
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

	boolean returnsOutput() {
		return !this.getInspector().getOutputType(function()).equals(Void.class);
	}

	protected boolean functionReturnsMessage(Object output) {
		return output instanceof Message;
	}
}

class TestExecutionContext implements Context {
	@Override
	public String eventId() {
		return null;
	}

	@Override
	public String timestamp() {
		return null;
	}

	@Override
	public String eventType() {
		return null;
	}

	@Override
	public String resource() {
		return null;
	}
}
