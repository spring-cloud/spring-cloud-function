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

import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import static org.apache.http.HttpHeaders.USER_AGENT;

/**
 * Event loop and necessary configurations to support AWS Lambda
 * Custom Runtime - https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html.
 *
 * @author Oleg Zhurakousky
 * @author Mark Sailes
 * @since 3.1.1
 *
 */
public final class CustomRuntimeEventLoop implements SmartLifecycle {

	private static Log logger = LogFactory.getLog(CustomRuntimeEventLoop.class);

	static final String LAMBDA_VERSION_DATE = "2018-06-01";
	private static final String LAMBDA_RUNTIME_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/next";
	private static final String LAMBDA_INVOCATION_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/{2}/response";
	private static final String USER_AGENT_VALUE = String.format(
			"spring-cloud-function/%s-%s",
			System.getProperty("java.runtime.version"),
			extractVersion());

	private final ConfigurableApplicationContext applicationContext;

	private volatile boolean running;

	private ExecutorService executor = Executors.newSingleThreadExecutor();

	public CustomRuntimeEventLoop(ConfigurableApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public void run() {
		this.running = true;
		this.executor.execute(() -> {
			eventLoop(this.applicationContext);
		});
	}

	@Override
	public void start() {
		this.run();
	}

	@Override
	public void stop() {
		this.executor.shutdownNow();
		this.running = false;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	@SuppressWarnings("unchecked")
	private void eventLoop(ConfigurableApplicationContext context) {
		Environment environment = context.getEnvironment();
		logger.info("Starting spring-cloud-function CustomRuntimeEventLoop");
		if (logger.isDebugEnabled()) {
			logger.debug("AWS LAMBDA ENVIRONMENT: " + System.getenv());
		}

		String runtimeApi = environment.getProperty("AWS_LAMBDA_RUNTIME_API");
		String eventUri = MessageFormat.format(LAMBDA_RUNTIME_URL_TEMPLATE, runtimeApi, LAMBDA_VERSION_DATE);
		if (logger.isDebugEnabled()) {
			logger.debug("Event URI: " + eventUri);
		}

		RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(eventUri)).header(USER_AGENT, USER_AGENT_VALUE).build();
		FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
		RestTemplate rest = new RestTemplate();
		JsonMapper mapper = context.getBean(JsonMapper.class);

		logger.info("Entering event loop");
		while (this.isRunning()) {
			logger.debug("Attempting to get new event");
			ResponseEntity<String> response = this.pollForData(rest, requestEntity);

			if (logger.isDebugEnabled()) {
				logger.debug("New Event received: " + response);
			}

			if (response != null) {
				FunctionInvocationWrapper function = locateFunction(environment, functionCatalog, response.getHeaders().getContentType());
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
				ResponseEntity<Object> result = rest.exchange(RequestEntity.post(URI.create(invocationUrl))
					.header(USER_AGENT, USER_AGENT_VALUE)
					.body(outputBody), Object.class);

				if (logger.isInfoEnabled()) {
					logger.info("Result POST status: " + result.getStatusCode());
				}
			}
		}
	}

	private ResponseEntity<String> pollForData(RestTemplate rest, RequestEntity<Void> requestEntity) {
		try {
			return rest.exchange(requestEntity, String.class);
		}
		catch (Exception e) {
			if (e instanceof SocketException) {
				this.stop();
				// ignore
			}
		}
		return null;
	}

	private FunctionInvocationWrapper locateFunction(Environment environment, FunctionCatalog functionCatalog, MediaType contentType) {
		String handlerName = environment.getProperty("DEFAULT_HANDLER");
		FunctionInvocationWrapper function = functionCatalog.lookup(handlerName, contentType.toString());
		if (function == null) {
			handlerName = environment.getProperty("_HANDLER");
			function = functionCatalog.lookup(handlerName, contentType.toString());
		}

		if (function == null) {
			function = functionCatalog.lookup(null, contentType.toString());
		}

		if (function == null) {
			handlerName = environment.getProperty("spring.cloud.function.definition");
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

	private MessageHeaders fromHttp(HttpHeaders headers) {
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

	private Collection<?> multi(Object value) {
		return value instanceof Collection ? (Collection<?>) value : Arrays.asList(value);
	}

	private static String extractVersion() {
		String path = CustomRuntimeEventLoop.class.getProtectionDomain().getCodeSource().getLocation().toString();
		int endIndex = path.lastIndexOf('.');
		if (endIndex < 0) {
			return "UNKNOWN-VERSION";
		}
		int startIndex = path.lastIndexOf("/") + 1;
		return path.substring(startIndex, endIndex).replace("spring-cloud-function-adapter-aws-", "");
	}
}
