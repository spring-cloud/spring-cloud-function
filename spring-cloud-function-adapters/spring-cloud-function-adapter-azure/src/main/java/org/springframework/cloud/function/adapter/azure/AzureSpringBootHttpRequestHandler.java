/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.function.adapter.azure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.reactivestreams.Publisher;
import org.springframework.cloud.function.adapter.azure.AzureSpringBootRequestHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;

import reactor.core.publisher.Flux;

/**
 * @param <I> input type
 * @author Markus Gulden
 */
public class AzureSpringBootHttpRequestHandler<I> extends
		AzureSpringBootRequestHandler<HttpRequestMessage<I>, HttpResponseMessage> {

	private HttpRequestMessage<I> input;

	public AzureSpringBootHttpRequestHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	public AzureSpringBootHttpRequestHandler() {
		super();
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected Object convertEvent(HttpRequestMessage<I> event) {

		if (event.getBody() != null) {
			if (functionAcceptsMessage()) {
				return new GenericMessage<I>(event.getBody(), getHeaders(event));
			}
			else {
				return event.getBody();
			}
		}
		else {
			if (functionAcceptsMessage()) {
				return new GenericMessage<Optional>(Optional.empty(), getHeaders(event));
			}
			else {
				return Optional.empty();
			}
		}
	}

	protected boolean functionAcceptsMessage() {

		return this.getInspector().isMessage(function());
	}

	private MessageHeaders getHeaders(HttpRequestMessage<I> event) {
		Map<String, Object> headers = new HashMap<String, Object>();

		if (event.getHeaders() != null) {
			headers.putAll(event.getHeaders());
		}
		if (event.getQueryParameters() != null) {
			headers.putAll(event.getQueryParameters());
		}
		if (event.getUri() != null) {
			headers.put("path", event.getUri().getPath());
		}

		if (event.getHttpMethod() != null) {
			headers.put("httpMethod", event.getHttpMethod().toString());
		}

		headers.put("request", event.getBody());
		return new MessageHeaders(headers);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected <O> O result(Object input, Publisher<?> output) {

		List<Object> result = new ArrayList<>();
		for (Object value : Flux.from(output).toIterable()) {
			result.add(convertOutput(value));
		}
		if (isSingleInput(getFunction(), input) && result.size() == 1) {
			HttpResponseMessage value = (HttpResponseMessage) result.get(0);
			return (O) value;
		}
		if (isSingleOutput(getFunction(), output) && result.size() == 1) {
			HttpResponseMessage value = (HttpResponseMessage) result.get(0);
			return (O) value;
		}

		O value = (O) result;
		return value;
	}

	@Override
	protected HttpResponseMessage convertOutput(Object output) {
		if (functionReturnsMessage(output)) {

			Message<?> message = (Message<?>) output;
			Builder builder = this.input
					.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.OK)
					.body(message.getPayload());
			for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
				builder = builder.header(entry.getKey(), entry.getValue().toString());
			}
			return builder.build();
		}
		else {

			return this.input
					.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.OK)
					.body(output).build();
		}
	}

	@Override
	public HttpResponseMessage handleRequest(HttpRequestMessage<I> event,
			ExecutionContext context) {
		this.input = event;
		Object response = super.handleRequest(event, context);
		if (returnsOutput()) {
			return (HttpResponseMessage) response;
		}
		else {
			return this.input
					.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.OK)
					.build();
		}
	}

	protected boolean returnsOutput() {
		return !this.getInspector().getOutputType(function()).equals(Void.class);
	}

	protected boolean functionReturnsMessage(Object output) {
		return output instanceof Message;
	}
}
