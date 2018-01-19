package org.springframework.cloud.function.adapter.aws;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

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
					(Integer) message.getHeaders().getOrDefault("statusCode", 200))
					.withHeaders(toResponseHeaders(message.getHeaders()))
					.withBody(serializeBody(message.getPayload()));
		}
		else {
			return new APIGatewayProxyResponseEvent().withStatusCode(200)
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
