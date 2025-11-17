/*
 * Copyright 2021-present the original author or authors.
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.amazonaws.services.lambda.runtime.Context;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.utils.JsonMasker;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StreamUtils;

/**
 * @author Oleg Zhurakousky
 * @author Anton Barkan
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

	private final static JsonMasker masker = JsonMasker.INSTANCE();

	private AWSLambdaUtils() {

	}

	static boolean isSupportedAWSType(Type type) {
		if (FunctionTypeUtils.isMessage(type) || FunctionTypeUtils.isPublisher(type)) {
			type = FunctionTypeUtils.getImmediateGenericType(type, 0);
		}
		Class<?> rawType = FunctionTypeUtils.getRawType(type);
		return rawType != null && rawType.getPackage() != null
				&& rawType.getPackage().getName().startsWith("com.amazonaws.services.lambda.runtime.events");
	}

	@SuppressWarnings("rawtypes")
	public static Message generateMessage(InputStream payload, Type inputType, boolean isSupplier,
			JsonMapper jsonMapper, Context context) throws IOException {
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

	public static Message<byte[]> generateMessage(byte[] payload, Type inputType, boolean isSupplier,
			JsonMapper jsonMapper) {
		return generateMessage(payload, inputType, isSupplier, jsonMapper, null);
	}

	private static String mask(String value) {
		return masker.mask(value);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Message<byte[]> generateMessage(byte[] payload, Type inputType, boolean isSupplier,
			JsonMapper jsonMapper, Context context) {
		if (logger.isInfoEnabled()) {
			logger.info("Received: " + mask(new String(payload, StandardCharsets.UTF_8)));
		}

		Object structMessage = jsonMapper.fromJson(payload, Object.class);
		boolean isApiGateway = structMessage instanceof Map
				&& (((Map<String, Object>) structMessage).containsKey("httpMethod")
						|| (((Map<String, Object>) structMessage).containsKey("routeKey")
								&& ((Map) structMessage).containsKey("version")));

		Message<byte[]> requestMessage;

		MessageBuilder builder = MessageBuilder.withPayload(
				structMessage instanceof Map msg && msg.containsKey("payload") ? (msg.get("payload")) : payload);
		if (isApiGateway) {
			builder.setHeader(AWSLambdaUtils.AWS_API_GATEWAY, true);
			if (JsonMapper.isJsonStringRepresentsCollection(((Map) structMessage).get("body"))) {
				builder.setHeader("payload", ((Map) structMessage).get("body"));
			}
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

	private static Object convertFromJsonIfNecessary(Object value, JsonMapper objectMapper) {
		if (JsonMapper.isJsonString(value)) {
			return objectMapper.fromJson(value, Object.class);
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	public static byte[] generateOutputFromObject(Message<?> requestMessage, Object output, JsonMapper objectMapper,
			Type functionOutputType) {
		Message<byte[]> responseMessage = null;
		if (output instanceof Publisher<?>) {
			List<Object> result = new ArrayList<>();
			Message<?> lastMessage = null;
			for (Object item : Flux.from((Publisher<?>) output).toIterable()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Response value: " + item);
				}
				if (item instanceof Message<?> message) {
					result.add(convertFromJsonIfNecessary(message.getPayload(), objectMapper));
					lastMessage = message;
				}
				else {
					result.add(convertFromJsonIfNecessary(item, objectMapper));
				}
			}

			byte[] resultPayload;
			if (result.size() == 1) {
				resultPayload = objectMapper.toJson(result.get(0));
			}
			else if (result.size() > 1) {
				resultPayload = objectMapper.toJson(result);
			}
			else {
				resultPayload = null;
			}

			if (resultPayload != null) {
				MessageBuilder<byte[]> messageBuilder = MessageBuilder.withPayload(resultPayload);
				if (lastMessage != null) {
					messageBuilder.copyHeaders(lastMessage.getHeaders());
				}
				responseMessage = messageBuilder.build();
			}
		}
		else {
			responseMessage = (Message<byte[]>) output;
		}
		return generateOutput(requestMessage, responseMessage, objectMapper, functionOutputType);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static byte[] generateOutput(Message requestMessage, Message<?> responseMessage, JsonMapper objectMapper,
			Type functionOutputType) {

		if (isSupportedAWSType(functionOutputType)) {
			return extractPayload((Message<Object>) responseMessage, objectMapper);
		}

		byte[] responseBytes = responseMessage == null ? "\"OK\"".getBytes()
				: extractPayload((Message<Object>) responseMessage, objectMapper);
		if (requestMessage.getHeaders().containsKey(AWS_API_GATEWAY)
				&& ((boolean) requestMessage.getHeaders().get(AWS_API_GATEWAY))) {
			Map<String, Object> response = new HashMap<String, Object>();
			response.put(IS_BASE64_ENCODED,
					responseMessage != null && responseMessage.getHeaders().containsKey(IS_BASE64_ENCODED)
							? responseMessage.getHeaders().get(IS_BASE64_ENCODED) : false);

			AtomicReference<MessageHeaders> headers = new AtomicReference<>();
			int statusCode = HttpStatus.OK.value();
			if (responseMessage != null) {
				headers.set(responseMessage.getHeaders());
				statusCode = headers.get().containsKey(STATUS_CODE) ? (int) headers.get().get(STATUS_CODE)
						: HttpStatus.OK.value();
			}

			response.put(STATUS_CODE, statusCode);
			if (isRequestKinesis(requestMessage)) {
				HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
				response.put("statusDescription", httpStatus.toString());
			}

			String body = responseMessage == null ? "\"OK\"" : new String(
					extractPayload((Message<Object>) responseMessage, objectMapper), StandardCharsets.UTF_8);
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
