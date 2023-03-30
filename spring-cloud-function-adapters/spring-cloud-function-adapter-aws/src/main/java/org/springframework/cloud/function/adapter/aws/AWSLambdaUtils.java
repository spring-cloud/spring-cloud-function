/*
 * Copyright 2021-2022 the original author or authors.
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.amazonaws.services.lambda.runtime.Context;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StreamUtils;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public final class AWSLambdaUtils {

	private static Log logger = LogFactory.getLog(AWSLambdaUtils.class);

	static final String AWS_API_GATEWAY = "aws-api-gateway";

	static final String AWS_EVENT = "aws-event";

	static final String IS_BASE64_ENCODED = "isBase64Encoded";

	static final String STATUS_CODE = "statusCode";

	static final String BODY = "body";

	static final String HEADERS = "headers";

	/**
	 * The name of the headers that stores AWS Context object.
	 */
	public static final String AWS_CONTEXT = "aws-context";

	private AWSLambdaUtils() {

	}

	static boolean isSupportedAWSType(Type inputType) {
		if (FunctionTypeUtils.isMessage(inputType) || FunctionTypeUtils.isPublisher(inputType)) {
			inputType = FunctionTypeUtils.getImmediateGenericType(inputType, 0);
		}
		String typeName = inputType.getTypeName();
		return typeName.equals("com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.S3Event")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.SNSEvent")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.SQSEvent")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent")
				|| typeName.equals("com.amazonaws.services.lambda.runtime.events.KinesisEvent");
	}

	@SuppressWarnings("rawtypes")
	public static Message generateMessage(InputStream payload, Type inputType, boolean isSupplier, JsonMapper jsonMapper, Context context) throws IOException {
		if (inputType != null && FunctionTypeUtils.isMessage(inputType)) {
			inputType = FunctionTypeUtils.getImmediateGenericType(inputType, 0);
		}
		if (inputType != null && InputStream.class.isAssignableFrom(FunctionTypeUtils.getRawType(inputType))) {
			MessageBuilder msgBuilder = MessageBuilder.withPayload(payload);
			if (context != null) {
				msgBuilder.setHeader(AWSLambdaUtils.AWS_CONTEXT, context);
			}
			return msgBuilder.build();
		}
		else {
			return generateMessage(StreamUtils.copyToByteArray(payload), inputType, isSupplier, jsonMapper, context);
		}
	}

	public static Message<byte[]> generateMessage(byte[] payload, Type inputType, boolean isSupplier, JsonMapper jsonMapper) {
		return generateMessage(payload, inputType, isSupplier, jsonMapper, null);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Message<byte[]> generateMessage(byte[] payload, Type inputType, boolean isSupplier, JsonMapper jsonMapper, Context context) {
		if (logger.isInfoEnabled()) {
			logger.info("Received: " + new String(payload, StandardCharsets.UTF_8));
		}


		Object structMessage = jsonMapper.fromJson(payload, Object.class);
		boolean isApiGateway = structMessage instanceof Map
				&& (((Map<String, Object>) structMessage).containsKey("httpMethod") ||
						(((Map<String, Object>) structMessage).containsKey("routeKey") && ((Map) structMessage).containsKey("version")));

		Message<byte[]> requestMessage;
		MessageBuilder<byte[]> builder = MessageBuilder.withPayload(payload);
		if (isApiGateway) {
			builder.setHeader(AWSLambdaUtils.AWS_API_GATEWAY, true);
		}
		if (!isSupplier && AWSLambdaUtils.isSupportedAWSType(inputType)) {
			builder.setHeader(AWSLambdaUtils.AWS_EVENT, true);
		}
		if (context != null) {
			builder.setHeader(AWSLambdaUtils.AWS_CONTEXT, context);
		}
		//
		if (structMessage instanceof Map && ((Map<String, Object>) structMessage).containsKey("headers")) {
			builder.copyHeaders((Map<String, Object>) ((Map<String, Object>) structMessage).get("headers"));
		}
		requestMessage = builder.build();
		return requestMessage;
	}

	private static byte[] extractPayload(Message<Object> msg, JsonMapper objectMapper) {
		if (msg.getPayload() instanceof byte[]) {
			return (byte[]) msg.getPayload();
		}
		else {
			return objectMapper.toJson(msg.getPayload());
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static byte[] generateOutput(Message requestMessage, Message<?> responseMessage,
			JsonMapper objectMapper, Type functionOutputType) {

		Class<?> outputClass = FunctionTypeUtils.getRawType(functionOutputType);
		if (outputClass != null) {
			String outputClassName = outputClass.getName();
			if (outputClassName.equals("com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse") ||
				outputClassName.equals("com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent") ||
				outputClassName.equals("com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent") ||
				outputClassName.equals("com.amazonaws.services.lambda.runtime.events.IamPolicyResponse")) {
				return extractPayload((Message<Object>) responseMessage, objectMapper);
			}
		}

		byte[] responseBytes = responseMessage  == null ? "\"OK\"".getBytes() : extractPayload((Message<Object>) responseMessage, objectMapper);
		if (requestMessage.getHeaders().containsKey(AWS_API_GATEWAY) && ((boolean) requestMessage.getHeaders().get(AWS_API_GATEWAY))) {
			Map<String, Object> response = new HashMap<String, Object>();
			response.put(IS_BASE64_ENCODED, responseMessage != null && responseMessage.getHeaders().containsKey(IS_BASE64_ENCODED)
					? responseMessage.getHeaders().get(IS_BASE64_ENCODED) : false);

			AtomicReference<MessageHeaders> headers = new AtomicReference<>();
			int statusCode = HttpStatus.OK.value();
			if (responseMessage != null) {
				headers.set(responseMessage.getHeaders());
				statusCode = headers.get().containsKey(STATUS_CODE)
						? (int) headers.get().get(STATUS_CODE)
						: HttpStatus.OK.value();
			}

			response.put(STATUS_CODE, statusCode);
			if (isRequestKinesis(requestMessage)) {
				HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
				response.put("statusDescription", httpStatus.toString());
			}

			String body = responseMessage == null
					? "\"OK\"" : new String(extractPayload((Message<Object>) responseMessage, objectMapper), StandardCharsets.UTF_8);
			response.put(BODY, body);
			if (responseMessage != null) {
				Map<String, String> responseHeaders = new HashMap<>();
				headers.get().keySet().forEach(key -> responseHeaders.put(key, headers.get().get(key).toString()));
				response.put(HEADERS, responseHeaders);
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
