package org.springframework.cloud.function.adapter.aws;

import com.amazonaws.serverless.proxy.internal.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.internal.model.AwsProxyResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import java.util.HashMap;
import java.util.Map;

public class SpringBootAwsProxyRequestHandler extends SpringBootRequestHandler<AwsProxyRequest, AwsProxyResponse> {

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private FunctionInspector inspector;

    public SpringBootAwsProxyRequestHandler(Class<?> configurationClass) {
        super(configurationClass);
    }

    public SpringBootAwsProxyRequestHandler() {
        super();
    }

    protected Object convertEvent(AwsProxyRequest event) {
        Object body = deserializeBody(event.getBody());
        if (functionAcceptsMessage()) {
            return new GenericMessage<>(body, getHeaders(event));
        } else {
            return body;
        }
    }

    private boolean functionAcceptsMessage() {
        return inspector.isMessage(function());
    }

    private Object deserializeBody(String json) {
        try {
            return mapper.readValue(json, getInputType());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot convert event", e);
        }
    }

    private MessageHeaders getHeaders(AwsProxyRequest event) {
        Map<String, Object> headers = new HashMap<>();
        headers.putAll(event.getHeaders());
        headers.put("request", event);
        return new MessageHeaders(headers);
    }

    protected AwsProxyResponse convertOutput(Object output) {
        if (functionReturnsMessage(output)) {
            Message message = (Message) output;
            return new AwsProxyResponse(
                    (Integer) message.getHeaders().getOrDefault("statusCode", 200),
                    toResponseHeaders(message.getHeaders()),
                    serializeBody(message.getPayload()));
        } else {
            return new AwsProxyResponse(
                    200,
                    new HashMap<>(),
                    serializeBody(output));
        }
    }

    private boolean functionReturnsMessage(Object output) {
        return output instanceof Message;
    }

    private Map<String, String> toResponseHeaders(MessageHeaders messageHeaders) {
        Map<String, String> responseHeaders = new HashMap<>();
        messageHeaders.forEach((key, value) -> responseHeaders.put(key, value.toString()));
        return responseHeaders;
    }

    private String serializeBody(Object body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot convert output", e);
        }
    }

}
