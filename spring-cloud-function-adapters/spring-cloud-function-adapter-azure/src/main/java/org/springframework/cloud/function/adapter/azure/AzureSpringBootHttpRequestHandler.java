/*
 * Copyright 2019-2019 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

/**
 * Implementation of HTTP Request Handler for Azure which supports
 * HttpRequestMessage and HttpResponseMessage the types required by
 * Azure Functions for HTTP-triggered functions.
 *
 * @param <I> input type
 * @author Markus Gulden
 *
 * @since 2.1
 * @deprecated since 3.2 in favor of {@link FunctionInvoker}
 */
@Deprecated
public class AzureSpringBootHttpRequestHandler<I> extends
		AzureSpringBootRequestHandler<HttpRequestMessage<I>, HttpResponseMessage> {

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

		return ((FunctionInvocationWrapper) function()).isInputTypeMessage();
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

	@SuppressWarnings("unchecked")
	@Override
	protected HttpResponseMessage convertOutput(Object input, Object output) {
		HttpRequestMessage<I> requestMessage = (HttpRequestMessage<I>) input;
		if (functionReturnsMessage(output)) {
			Message<?> message = (Message<?>) output;
			Builder builder = requestMessage
					.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.OK)
					.body(message.getPayload());
			for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
				if (entry.getValue() != null) {
					builder = builder.header(entry.getKey(), entry.getValue().toString());
				}
			}
			return builder.build();
		}
		else {
			return requestMessage
					.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.OK)
					.body(output).build();
		}
	}

	@Override
	public HttpResponseMessage handleRequest(HttpRequestMessage<I> event,
			ExecutionContext context) {
		HttpResponseMessage result = super.handleRequest(event, context);
		if (result == null) {
			result = event
					.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.OK)
					.build();
		}
		return result;
	}

	protected boolean functionReturnsMessage(Object output) {
		return output instanceof Message;
	}
}
