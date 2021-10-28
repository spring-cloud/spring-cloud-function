/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;

import java.io.ByteArrayInputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import com.amazonaws.services.lambda.runtime.serialization.events.LambdaEventSerializers;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
final class AWSLambdaUtils {

	private static Log logger = LogFactory.getLog(AWSLambdaUtils.class);

	static final String AWS_API_GATEWAY = "aws-api-gateway";

	private AWSLambdaUtils() {

	}

	public static Message<byte[]> generateMessage(byte[] payload, MessageHeaders headers,
			Type inputType, JsonMapper objectMapper) {
		return generateMessage(payload, headers, inputType, objectMapper, null);
	}

	static boolean isSupportedAWSType(Type inputType) {
		String typeName = inputType.getTypeName();
		return typeName.equals("com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.S3Event")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.SNSEvent")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.SQSEvent")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.KinesisEvent");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Message<byte[]> generateMessage(byte[] payload, MessageHeaders headers,
			Type inputType, JsonMapper objectMapper, @Nullable Context awsContext) {

		if (logger.isInfoEnabled()) {
			logger.info("Incoming JSON Event: " + new String(payload));
		}

		if (FunctionTypeUtils.isMessage(inputType)) {
			inputType = FunctionTypeUtils.getImmediateGenericType(inputType, 0);
		}

		MessageBuilder messageBuilder = null;
		if (inputType != null && isSupportedAWSType(inputType)) {
			PojoSerializer<?> serializer = LambdaEventSerializers.serializerFor(FunctionTypeUtils.getRawType(inputType), Thread.currentThread().getContextClassLoader());
			Object event = serializer.fromJson(new ByteArrayInputStream(payload));
			messageBuilder = MessageBuilder.withPayload(event);
			if (event instanceof APIGatewayProxyRequestEvent || event instanceof APIGatewayV2HTTPEvent) {
				messageBuilder.setHeader(AWS_API_GATEWAY, true);
				logger.info("Incoming request is API Gateway");
			}
		}
		else {
			Object request;
			try {
				request = objectMapper.fromJson(payload, Object.class);
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}

			if (request instanceof Map) {
				logger.info("Incoming MAP: " + request);
				if (((Map) request).containsKey("httpMethod")) { //API Gateway
					logger.info("Incoming request is API Gateway");
					boolean mapInputType = (inputType instanceof ParameterizedType && ((Class<?>) ((ParameterizedType) inputType).getRawType()).isAssignableFrom(Map.class));
					if (mapInputType) {
						messageBuilder = MessageBuilder.withPayload(request).setHeader("httpMethod", ((Map) request).get("httpMethod"));
						messageBuilder.setHeader(AWS_API_GATEWAY, true);
					}
					else {
						messageBuilder = createMessageBuilderForPOJOFunction(objectMapper, (Map) request);
					}
				}
				else if ((((Map) request).containsKey("routeKey") && ((Map) request).containsKey("version"))) {
					logger.info("Incoming request is API Gateway v2.0");
					messageBuilder = createMessageBuilderForPOJOFunction(objectMapper, (Map) request);
				}
				Object providedHeaders = ((Map) request).remove("headers");
				if (providedHeaders != null && providedHeaders instanceof Map) {
					messageBuilder.removeHeader("headers");
					messageBuilder.copyHeaders((Map<String, Object>) providedHeaders);
				}
			}
			else if (request instanceof Iterable) {
				messageBuilder = MessageBuilder.withPayload(request);
			}
		}


		if (messageBuilder == null) {
			messageBuilder = MessageBuilder.withPayload(payload);
		}
		if (awsContext != null) {
			messageBuilder.setHeader("aws-context", awsContext);
		}
		logger.info("Incoming request headers: " + headers);

		return messageBuilder.copyHeaders(headers).build();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static MessageBuilder createMessageBuilderForPOJOFunction(JsonMapper objectMapper, Map request) {
		Object body = request.remove("body");
		try {
			body = body instanceof String
					? String.valueOf(body).getBytes(StandardCharsets.UTF_8)
							: objectMapper.toJson(body);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
		logger.info("Body is " + body);

		MessageBuilder messageBuilder = MessageBuilder.withPayload(body).copyHeaders(request);
		messageBuilder.setHeader(AWS_API_GATEWAY, true);
		return messageBuilder;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static byte[] generateOutput(Message requestMessage, Message<byte[]> responseMessage,
			JsonMapper objectMapper, Type functionOutputType) {

		Class<?> outputClass = FunctionTypeUtils.getRawType(functionOutputType);
		if (outputClass != null) {
			String outputClassName = outputClass.getName();
			if (outputClassName.equals("com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse") ||
				outputClassName.equals("com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent")) {
				return responseMessage.getPayload();
			}
		}

		byte[] responseBytes = responseMessage  == null ? "\"OK\"".getBytes() : responseMessage.getPayload();
		if (requestMessage.getHeaders().containsKey(AWS_API_GATEWAY) && ((boolean) requestMessage.getHeaders().get(AWS_API_GATEWAY))) {
			Map<String, Object> response = new HashMap<String, Object>();
			response.put("isBase64Encoded", false);

			AtomicReference<MessageHeaders> headers = new AtomicReference<>();
			int statusCode = HttpStatus.OK.value();
			if (responseMessage != null) {
				headers.set(responseMessage.getHeaders());
				statusCode = headers.get().containsKey("statusCode")
						? (int) headers.get().get("statusCode")
						: HttpStatus.OK.value();
			}

			response.put("statusCode", statusCode);
			if (isRequestKinesis(requestMessage)) {
				HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
				response.put("statusDescription", httpStatus.toString());
			}

			String body = responseMessage == null
					? "\"OK\"" : new String(responseMessage.getPayload(), StandardCharsets.UTF_8).replaceAll("\\\"", "");
			response.put("body", body);

			if (responseMessage != null) {
				Map<String, String> responseHeaders = new HashMap<>();
				headers.get().keySet().forEach(key -> responseHeaders.put(key, headers.get().get(key).toString()));
				response.put("headers", responseHeaders);
			}

			try {
				responseBytes = objectMapper.toJson(response);
			}
			catch (Exception e) {
				throw new IllegalStateException("Failed to serialize AWS Lambda output", e);
			}
		}
		return responseBytes;
	}

	private static boolean isRequestKinesis(Message<Object> requestMessage) {
		return requestMessage.getHeaders().containsKey("Records");
	}
}
