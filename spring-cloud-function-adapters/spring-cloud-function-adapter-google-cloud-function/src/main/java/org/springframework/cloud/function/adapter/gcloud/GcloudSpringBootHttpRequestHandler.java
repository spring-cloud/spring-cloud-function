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
import java.util.List;
import java.util.Map;

import com.google.cloud.functions.Context;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

/**

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

		if (functionAcceptsMessage()) {
			Map headers = event.getHeaders();
			return new GenericMessage<String>(sb.toString(), headers);
		}
		else {
			return sb.toString();
		}

	}

	protected boolean functionAcceptsMessage() {
		return this.getInspector().isMessage(function());
	}

	@SuppressWarnings("unchecked")
	protected <T> T result(Object input, Publisher<?> output) {
		List<T> result = new ArrayList<>();
		for (Object value : Flux.from(output).toIterable()) {
			result.add((T) convertOutput(value));
		}
		if (isSingleValue(input) && result.size() == 1) {
			return result.get(0);
		}
		return (T) result;
	}
	//
	// protected boolean acceptsInput() {
	// 	return !this.getInspector().getInputType(function()).equals(Void.class);
	// }
	//
	// protected boolean returnsOutput() {
	// 	return !this.getInspector().getOutputType(function()).equals(Void.class);
	// }

	private boolean isSingleValue(Object input) {
		return !(input instanceof Collection);
	}

	private Flux<?> extract(Object input) {
		// if (input instanceof Collection) {
		// 	return Flux.fromIterable((Iterable<?>) input);
		// }
		return Flux.just(input);
	}

	// protected Object convertEvent(E event) {
	// 	return event;
	// }

	public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
		Thread.currentThread()
			.setContextClassLoader(GcloudSpringBootHttpRequestHandler.class.getClassLoader());
		initialize(new TestExecutionContext("abc"));

		Publisher<?> output = apply(extract(convert(httpRequest)));
		BufferedWriter writer = httpResponse.getWriter();
		writer.write(gson.toJson((Object) result(httpRequest, output)));
		writer.flush();
	}

	protected O convertOutput(Object output) {
		if (functionReturnsMessage(output)) {
			Message<?> message = (Message<?>) output;
			return (O) message.getPayload();
			// for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
			// 	builder = builder.header(entry.getKey(), entry.getValue().toString());
			// }
			// return builder.build();
		}
		else {
			return (O) output;
		}
	}


	protected boolean functionReturnsMessage(Object output) {
		return output instanceof Message;
	}
}
class TestExecutionContext implements Context {

	private String name;

	TestExecutionContext(String name) {
		this.name = name;
	}

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
