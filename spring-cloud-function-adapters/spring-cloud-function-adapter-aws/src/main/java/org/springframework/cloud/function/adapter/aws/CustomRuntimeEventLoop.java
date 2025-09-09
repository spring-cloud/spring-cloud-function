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

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.LambdaRuntime;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import static org.apache.http.HttpHeaders.USER_AGENT;

/**
 * Event loop and necessary configurations to support AWS Lambda
 * Custom Runtime - https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html.
 *
 * @author Oleg Zhurakousky
 * @author Mark Sailes
 * @author Rahul Lokurte
 * @since 3.1.1
 *
 */
public final class CustomRuntimeEventLoop implements SmartLifecycle {

	private static Log logger = LogFactory.getLog(CustomRuntimeEventLoop.class);

	static final String LAMBDA_VERSION_DATE = "2018-06-01";
	private static final String LAMBDA_ERROR_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/{2}/error";
	private static final String LAMBDA_RUNTIME_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/next";
	private static final String LAMBDA_INVOCATION_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/{2}/response";
	private static final String USER_AGENT_VALUE = String.format(
			"spring-cloud-function/%s-%s",
			System.getProperty("java.runtime.version"),
			extractVersion());

	private final ConfigurableApplicationContext applicationContext;

	private volatile boolean running;

	private ExecutorService executor = Executors.newSingleThreadExecutor();

	private FunctionInvocationWrapper routingFunction;

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

			Context clientContext = generateClientContext(response.getHeaders());

			if (logger.isDebugEnabled()) {
				logger.debug("New Event received: " + response);
			}

			if (response != null && response.hasBody()) {
				String requestId = response.getHeaders().getFirst("Lambda-Runtime-Aws-Request-Id");
				try {
					FunctionInvocationWrapper function = locateFunction(environment, functionCatalog, response.getHeaders());

					ByteArrayInputStream is = new ByteArrayInputStream(response.getBody().getBytes(StandardCharsets.UTF_8));
					Message<?> requestMessage = AWSLambdaUtils.generateMessage(is, function.getInputType(), function.isSupplier(), mapper, clientContext);
					Object functionResponse = function.apply(requestMessage);

					byte[] responseBytes = AWSLambdaUtils.generateOutputFromObject(requestMessage, functionResponse, mapper, function.getOutputType());

					String invocationUrl = MessageFormat
							.format(LAMBDA_INVOCATION_URL_TEMPLATE, runtimeApi, LAMBDA_VERSION_DATE, requestId);

					ResponseEntity<Object> result = rest.exchange(RequestEntity.post(URI.create(invocationUrl))
						.header(USER_AGENT, USER_AGENT_VALUE)
						.body(responseBytes), Object.class);

					if (logger.isInfoEnabled()) {
						logger.info("Result POST status: " + result);
					}
				}
				catch (Exception e) {
					e.printStackTrace();
					this.propagateAwsError(requestId, e, mapper, runtimeApi, rest);
				}
			}
		}
	}

	private Context generateClientContext(HttpHeaders headers) {

		Map<String, String> environment = System.getenv();

		Context context = new Context() {

			@Override
			public int getRemainingTimeInMillis() {
				long now = System.currentTimeMillis();
				if (!headers.containsKey("Lambda-Runtime-Deadline-Ms")) {
					return 0;
				}
				int delta = (int) (Long.parseLong(headers.getFirst("Lambda-Runtime-Deadline-Ms")) - now);
				return delta > 0 ? delta : 0;
			}

			@Override
			public int getMemoryLimitInMB() {
				if (!environment.containsKey("AWS_LAMBDA_FUNCTION_MEMORY_SIZE")) {
					return 128;
				}
				return Integer.parseInt(environment.getOrDefault("AWS_LAMBDA_FUNCTION_MEMORY_SIZE", "128"));
			}

			@Override
			public LambdaLogger getLogger() {
				return LambdaRuntime.getLogger();
			}

			@Override
			public String getLogStreamName() {
				return environment.get("LOG_STREAM_NAME");
			}

			@Override
			public String getLogGroupName() {
				return environment.get("LOG_GROUP_NAME");
			}

			@Override
			public String getInvokedFunctionArn() {
				return headers.getFirst("Lambda-Runtime-Invoked-Function-Arn");
			}

			@Override
			public CognitoIdentity getIdentity() {
				return null;
			}

			@Override
			public String getFunctionVersion() {
				return environment.get("FUNCTION_VERSION");
			}

			@Override
			public String getFunctionName() {
				return environment.get("FUNCTION_NAME");
			}

			@Override
			public ClientContext getClientContext() {
				return null;
			}

			@Override
			public String getAwsRequestId() {
				return headers.getFirst("Lambda-Runtime-Aws-Request-Id");
			}

			public String toString() {
				return "FUNCTION NAME: " + getFunctionName() + ", FUNCTION VERSION: " + getFunctionVersion()
						+ ", FUNCTION ARN: " + getInvokedFunctionArn() + ", FUNCTION MEM LIMIT: " + getMemoryLimitInMB()
						+ ", FUNCTION DEADLINE: " + getRemainingTimeInMillis();
			}
		};
		return context;
	}

	private void propagateAwsError(String requestId, Exception e, JsonMapper mapper, String runtimeApi, RestTemplate rest) {
		String errorMessage = e.getMessage();
		String errorType = e.getClass().getSimpleName();
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String stackTrace = sw.toString();
		Map<String, String> em = new HashMap<>();
		em.put("errorMessage", errorMessage);
		em.put("errorType", errorType);
		em.put("stackTrace", stackTrace);
		byte[] outputBody = mapper.toJson(em);
		try {
			String errorUrl = MessageFormat.format(LAMBDA_ERROR_URL_TEMPLATE, runtimeApi, LAMBDA_VERSION_DATE, requestId);
			ResponseEntity<Object> result = rest.exchange(RequestEntity.post(URI.create(errorUrl))
					.header(USER_AGENT, USER_AGENT_VALUE)
					.body(outputBody), Object.class);
			if (logger.isInfoEnabled()) {
				logger.info("Result ERROR status: " + result.getStatusCode());
			}
		}
		catch (Exception e2) {
			throw new IllegalArgumentException("Failed to report error", e2);
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

	private FunctionInvocationWrapper locateFunction(Environment environment, FunctionCatalog functionCatalog,
			HttpHeaders httpHeaders) {
		if (this.routingFunction != null) {
			return this.routingFunction;
		}
		MediaType contentType = httpHeaders.getContentType();
		String handlerName = environment.getProperty("DEFAULT_HANDLER");
		if (logger.isDebugEnabled()) {
			logger.debug("Value of DEFAULT_HANDLER env: " + handlerName);
		}
		FunctionInvocationWrapper function = functionCatalog.lookup(handlerName, contentType.toString());
		if (function == null) {
			logger.debug("Could not locate function under DEFAULT_HANDLER");
			handlerName = environment.getProperty("_HANDLER");
			if (logger.isDebugEnabled()) {
				logger.debug("Value of _HANDLER env: " + handlerName);
			}
			function = functionCatalog.lookup(handlerName, contentType.toString());
		}

		if (function == null) {
			logger.debug("Could not locate function under _HANDLER");
			function = functionCatalog.lookup((String) null, contentType.toString());
		}

		if (function == null) {
			logger.info("Could not determine default function");
			handlerName = environment.getProperty("spring.cloud.function.definition");
			if (logger.isDebugEnabled()) {
				logger.debug("Value of 'spring.cloud.function.definition' env: " + handlerName);
			}
			function = functionCatalog.lookup(handlerName, contentType.toString());
		}

		if (function == null) {
			logger.info("Could not determine DEFAULT_HANDLER, _HANDLER or 'spring.cloud.function.definition'");
			handlerName = httpHeaders.getFirst("spring.cloud.function.definition");
			if (logger.isDebugEnabled()) {
				logger.debug("Value of 'spring.cloud.function.definition' header: " + handlerName);
			}
			function = functionCatalog.lookup(handlerName, contentType.toString());
		}

		if (function == null) {
			this.routingFunction = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME, "application/json");
			if (this.routingFunction != null && logger.isInfoEnabled()) {
				logger.info("Will default to RoutingFunction, since multiple functions available in FunctionCatalog."
								+ "Expecting 'spring.cloud.function.definition' or 'spring.cloud.function.routing-expression' as Message headers. "
								+ "If invocation is over API Gateway, Message headers can be provided as HTTP headers.");
			}
			function = this.routingFunction;
		}

		Assert.notNull(function, "Failed to locate function. Tried locating default function, "
				+ "function by 'DEFAULT_HANDLER', '_HANDLER' env variable as well as'spring.cloud.function.definition'. "
				+ "Functions available in catalog are: " + functionCatalog.getNames(null));
		if (function != null && logger.isInfoEnabled()) {
			logger.info("Located function " + function.getFunctionDefinition());
		}
		return function;
	}

	private static String extractVersion() {
		try {
			String path = CustomRuntimeEventLoop.class.getProtectionDomain().getCodeSource().getLocation().toString();
			int endIndex = path.lastIndexOf('.');
			if (endIndex < 0) {
				return "UNKNOWN-VERSION";
			}
			int startIndex = path.lastIndexOf("/") + 1;
			return path.substring(startIndex, endIndex).replace("spring-cloud-function-adapter-aws-", "");
		}
		catch (Exception e) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to detect version", e);
			}
			return "UNKNOWN-VERSION";
		}

	}
}
