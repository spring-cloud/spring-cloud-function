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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

/**
 * Event loop and necessary configurations to support AWS Lambda
 * Custom Runtime - https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html.
 *
 * @author Oleg Zhurakousky
 * @since 3.1.1
 *
 */
final class CustomRuntimeEventLoop {

	private static Log logger = LogFactory.getLog(CustomRuntimeEventLoop.class);

	static final String LAMBDA_VERSION_DATE = "2018-06-01";
	private static final String LAMBDA_RUNTIME_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/next";
	private static final String LAMBDA_INVOCATION_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/{2}/response";

	private CustomRuntimeEventLoop() {
	}

	@SuppressWarnings("unchecked")
	static void eventLoop(ApplicationContext context) {
		logger.info("Starting spring-cloud-function CustomRuntimeEventLoop");
		if (logger.isDebugEnabled()) {
			logger.debug("AWS LAMBDA ENVIRONMENT: " + System.getenv());
		}

		String runtimeApi = System.getenv("AWS_LAMBDA_RUNTIME_API");
		String eventUri = MessageFormat.format(LAMBDA_RUNTIME_URL_TEMPLATE, runtimeApi, LAMBDA_VERSION_DATE);
		if (logger.isDebugEnabled()) {
			logger.debug("Event URI: " + eventUri);
		}

		RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(eventUri)).build();
		FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
		RestTemplate rest = new RestTemplate();
		ObjectMapper mapper = context.getBean(ObjectMapper.class);

		logger.info("Entering event loop");
		while (isContinue()) {
			logger.debug("Attempting to get new event");
			ResponseEntity<String> response = rest.exchange(requestEntity, String.class);
			if (logger.isDebugEnabled()) {
				logger.debug("New Event received: " + response);
			}

			FunctionInvocationWrapper function = locateFunction(functionCatalog, response.getHeaders().getContentType());
			Message<byte[]> eventMessage = AWSLambdaUtils.generateMessage(response.getBody().getBytes(StandardCharsets.UTF_8),
					fromHttp(response.getHeaders()), function.getInputType(), mapper);
			if (logger.isDebugEnabled()) {
				logger.debug("Event message: " + eventMessage);
			}

			String requestId = response.getHeaders().getFirst("Lambda-Runtime-Aws-Request-Id");
			String invocationUrl = MessageFormat
					.format(LAMBDA_INVOCATION_URL_TEMPLATE, runtimeApi, LAMBDA_VERSION_DATE, requestId);

			Message<byte[]> responseMessage = (Message<byte[]>) function.apply(eventMessage);

			if (responseMessage != null && logger.isDebugEnabled()) {
				logger.debug("Reply from function: " + responseMessage);
			}

			byte[] outputBody = AWSLambdaUtils.generateOutput(eventMessage, responseMessage, mapper, function.getOutputType());
			ResponseEntity<Object> result = rest
					.exchange(RequestEntity.post(URI.create(invocationUrl)).body(outputBody), Object.class);

			if (logger.isInfoEnabled()) {
				logger.info("Result POST status: " + result.getStatusCode());
			}
		}
	}

	private static boolean isContinue() {
		return Boolean.parseBoolean(System.getProperty("CustomRuntimeEventLoop.continue", "true"));
	}

	private static FunctionInvocationWrapper locateFunction(FunctionCatalog functionCatalog, MediaType contentType) {
		String handlerName = System.getenv("DEFAULT_HANDLER");
		FunctionInvocationWrapper function = functionCatalog.lookup(handlerName, contentType.toString());
		if (function == null) {
			handlerName = System.getenv("_HANDLER");
			function = functionCatalog.lookup(handlerName, contentType.toString());
		}

		if (function == null) {
			function = functionCatalog.lookup(null, contentType.toString());
		}

		if (function == null) {
			handlerName = System.getenv("spring.cloud.function.definition");
			function = functionCatalog.lookup(handlerName, contentType.toString());
		}

		if (function == null) {
			function = functionCatalog.lookup(null, contentType.toString());
		}

		Assert.notNull(function, "Failed to locate function. Tried locating default function, "
				+ "function by 'DEFAULT_HANDLER', '_HANDLER' env variable as well as'spring.cloud.function.definition'. "
				+ "Functions available in catalog are: " + functionCatalog.getNames(null));
		if (function != null && logger.isInfoEnabled()) {
			logger.info("Located function " + function.getFunctionDefinition());
		}
		return function;
	}

	private static MessageHeaders fromHttp(HttpHeaders headers) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (String name : headers.keySet()) {
			Collection<?> values = multi(headers.get(name));
			name = name.toLowerCase();
			Object value = values == null ? null
					: (values.size() == 1 ? values.iterator().next() : values);
			if (name.toLowerCase().equals(HttpHeaders.CONTENT_TYPE.toLowerCase())) {
				name = MessageHeaders.CONTENT_TYPE;
			}
			map.put(name, value);
		}
		return new MessageHeaders(map);
	}

	private static Collection<?> multi(Object value) {
		return value instanceof Collection ? (Collection<?>) value : Arrays.asList(value);
	}
}
