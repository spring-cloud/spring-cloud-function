/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

/**
 *
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 */
public class SpringBootApiGatewayRequestHandler extends
		SpringBootRequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private FunctionInspector inspector;

	public SpringBootApiGatewayRequestHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	public SpringBootApiGatewayRequestHandler() {
		super();
	}

	@Override
	protected Object convertEvent(APIGatewayProxyRequestEvent event) {
		Object body = deserializeBody(event.getBody());
		if (functionAcceptsMessage()) {
			return new GenericMessage<>(body, getHeaders(event));
		}
		else {
			return body;
		}
	}

	private boolean functionAcceptsMessage() {
		return inspector.isMessage(function());
	}

	private Object deserializeBody(String json) {
		try {
			return mapper.readValue(json, getInputType());
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert event", e);
		}
	}

	private MessageHeaders getHeaders(APIGatewayProxyRequestEvent event) {
		Map<String, Object> headers = new HashMap<>();
		if (event.getHeaders() != null) {
			headers.putAll(event.getHeaders());
		}
		headers.put("request", event);
		return new MessageHeaders(headers);
	}

	@Override
	protected APIGatewayProxyResponseEvent convertOutput(Object output) {
		if (functionReturnsMessage(output)) {
			Message<?> message = (Message<?>) output;
			return new APIGatewayProxyResponseEvent().withStatusCode(
					(Integer) message.getHeaders().getOrDefault("statuscode", HttpStatus.OK.value()))
					.withHeaders(toResponseHeaders(message.getHeaders()))
					.withBody(serializeBody(message.getPayload()));
		}
		else {
			return new APIGatewayProxyResponseEvent().withStatusCode(HttpStatus.OK.value())
					.withBody(serializeBody(output));

		}
	}

	private boolean functionReturnsMessage(Object output) {
		return output instanceof Message;
	}

	private Map<String, String> toResponseHeaders(MessageHeaders messageHeaders) {
		Map<String, String> responseHeaders = new HashMap<>();
		messageHeaders
				.forEach((key, value) -> responseHeaders.put(key, value.toString()));
		return responseHeaders;
	}

	private String serializeBody(Object body) {
		try {
			return mapper.writeValueAsString(body);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot convert output", e);
		}
	}

}
