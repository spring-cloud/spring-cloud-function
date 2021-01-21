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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
final class AWSLambdaUtils {

	private static Log logger = LogFactory.getLog(AWSLambdaUtils.class);

	private AWSLambdaUtils() {

	}

	public static Message<byte[]> generateMessage(byte[] payload, MessageHeaders headers,
			FunctionInvocationWrapper function, JsonMapper mapper) {
		return generateMessage(payload, headers, function, mapper, null);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Message<byte[]> generateMessage(byte[] payload, MessageHeaders headers,
			FunctionInvocationWrapper function, JsonMapper mapper, @Nullable Context awsContext) {

		if (logger.isInfoEnabled()) {
			logger.info("Incoming JSON for ApiGateway Event: " + new String(payload));
		}

		MessageBuilder messageBuilder = null;
		Object request = mapper.fromJson(payload, Object.class);
		Type inputType = function.getInputType();
		if (FunctionTypeUtils.isMessage(inputType)) {
			inputType = FunctionTypeUtils.getImmediateGenericType(inputType, 0);
		}
		boolean mapInputType = (inputType instanceof ParameterizedType && ((Class<?>) ((ParameterizedType) inputType).getRawType()).isAssignableFrom(Map.class));
		if (request instanceof Map) {
			Map<String, ?> requestMap = (Map<String, ?>) request;
			if (requestMap.containsKey("Records")) {
				List<Map<String, ?>> records = (List<Map<String, ?>>) requestMap.get("Records");
				Assert.notEmpty(records, "Incoming event has no records: " + requestMap);
				logEvent(records);
				messageBuilder = MessageBuilder.withPayload(payload);
			}
			else if (requestMap.containsKey("httpMethod")) { // API Gateway
				logger.info("Incoming request is API Gateway");
				if (inputType.getTypeName().endsWith(APIGatewayProxyRequestEvent.class.getSimpleName())) {
					APIGatewayProxyRequestEvent gatewayEvent = mapper.fromJson(requestMap, APIGatewayProxyRequestEvent.class);
					messageBuilder = MessageBuilder.withPayload(gatewayEvent);
				}
				else if (mapInputType) {
					messageBuilder = MessageBuilder.withPayload(requestMap).setHeader("httpMethod", requestMap.get("httpMethod"));
				}
				else {
					Object body = requestMap.remove("body");
					body = body instanceof String ? String.valueOf(body).getBytes(StandardCharsets.UTF_8) : mapper.toJson(body);
					messageBuilder = MessageBuilder.withPayload(body).copyHeaders(requestMap);
				}
			}
		}
		if (messageBuilder == null) {
			messageBuilder = MessageBuilder.withPayload(payload);
		}
		if (awsContext != null) {
			messageBuilder.setHeader("aws-context", awsContext);
		}
		return messageBuilder.copyHeaders(headers).build();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static byte[] generateOutput(Message requestMessage, Message<byte[]> responseMessage,
			JsonMapper mapper) {
		byte[] responseBytes = responseMessage.getPayload();
		if (requestMessage.getHeaders().containsKey("httpMethod") || requestMessage.getPayload() instanceof APIGatewayProxyRequestEvent) { // API Gateway
			Map<String, Object> response = new HashMap<String, Object>();
			response.put("isBase64Encoded", false);

			MessageHeaders headers = responseMessage.getHeaders();
			int statusCode = headers.containsKey("statusCode")
					? (int) headers.get("statusCode")
					: 200;

			response.put("statusCode", statusCode);
			if (isRequestKinesis(requestMessage)) {
				HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
				response.put("statusDescription", httpStatus.toString());
			}

			String body = new String(responseMessage.getPayload(), StandardCharsets.UTF_8).replaceAll("\\\"", "\"");
			response.put("body", body);

			Map<String, String> responseHeaders = new HashMap<>();
			headers.keySet().forEach(key -> responseHeaders.put(key, headers.get(key).toString()));

			response.put("headers", responseHeaders);
			responseBytes = mapper.toJson(response);
		}

		return responseBytes;
	}

	private static void logEvent(List<Map<String, ?>> records) {
		if (isKinesisEvent(records.get(0))) {
			logger.info("Incoming request is Kinesis Event");
		}
		else if (isS3Event(records.get(0))) {
			logger.info("Incoming request is S3 Event");
		}
		else if (isSNSEvent(records.get(0))) {
			logger.info("Incoming request is SNS Event");
		}
		else {
			logger.info("Incoming request is SQS Event");
		}
	}

	private static boolean isRequestKinesis(Message<Object> requestMessage) {
		return requestMessage.getHeaders().containsKey("Records");
	}

	private static boolean isSNSEvent(Map<String, ?> record) {
		return record.containsKey("Sns");
	}

	private static boolean isS3Event(Map<String, ?> record) {
		return record.containsKey("s3");
	}

	private static boolean isKinesisEvent(Map<String, ?> record) {
		return record.containsKey("kinesis");
	}
}
