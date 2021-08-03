/*
 * Copyright 2019-2021 the original author or authors.
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.utils.FunctionClassUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 *
 *        see
 *        https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-output-format
 */
public class FunctionInvoker implements RequestStreamHandler {

	private static Log logger = LogFactory.getLog(FunctionInvoker.class);

	private ObjectMapper objectMapper;

	private FunctionInvocationWrapper function;

	public FunctionInvoker() {
		this.start();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
		final byte[] payload = StreamUtils.copyToByteArray(input);

		if (logger.isInfoEnabled()) {
			logger.info("Received: " + new String(payload, StandardCharsets.UTF_8));
		}

		Message requestMessage = AWSLambdaUtils
				.generateMessage(payload, new MessageHeaders(Collections.emptyMap()), function.getInputType(), this.objectMapper, context);

		try {
			Object response = this.function.apply(requestMessage);
			byte[] responseBytes = this.buildResult(requestMessage, response);
			StreamUtils.copy(responseBytes, output);
		}
		catch (Exception e) {
			logger.error(e);
			StreamUtils.copy(this.buildExceptionResult(requestMessage, e), output);
		}
	}

	private byte[] buildExceptionResult(Message<?> requestMessage, Exception exception) throws IOException {
		APIGatewayProxyResponseEvent event = new APIGatewayProxyResponseEvent();
		event.setStatusCode(HttpStatus.EXPECTATION_FAILED.value());
		event.setBody(exception.getMessage());
		return this.objectMapper.writeValueAsBytes(event);
	}

	@SuppressWarnings("unchecked")
	private byte[] buildResult(Message<?> requestMessage, Object output) throws IOException {
		Message<byte[]> responseMessage;
		if (output instanceof Publisher<?>) {
			List<Object> result = new ArrayList<>();
			for (Object value : Flux.from((Publisher<?>) output).toIterable()) {
				if (logger.isInfoEnabled()) {
					logger.info("Response value: " + value);
				}
				result.add(value);
			}
			if (result.size() > 1) {
				output = result;
			}
			else {
				output = result.get(0);
			}

			if (logger.isInfoEnabled()) {
				logger.info("OUTPUT: " + output + " - " + output.getClass().getName());
			}

			byte[] payload = this.objectMapper.writeValueAsBytes(output);
			responseMessage = MessageBuilder.withPayload(payload).build();
		}
		else {
			responseMessage = (Message<byte[]>) output;
		}
		return AWSLambdaUtils.generateOutput(requestMessage, responseMessage, this.objectMapper, function.getOutputType());
	}

	private void start() {
		Class<?> startClass = FunctionClassUtils.getStartClass();
		String[] properties = new String[] {"--spring.cloud.function.web.export.enabled=false", "--spring.main.web-application-type=none"};
		ConfigurableApplicationContext context = ApplicationContextInitializer.class.isAssignableFrom(startClass)
				? FunctionalSpringApplication.run(startClass, properties)
						: SpringApplication.run(FunctionClassUtils.getStartClass(), properties);

		Environment environment = context.getEnvironment();
		String functionName = environment.getProperty("spring.cloud.function.definition");
		FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
		this.objectMapper = context.getBean(ObjectMapper.class);

		if (logger.isInfoEnabled()) {
			logger.info("Locating function: '" + functionName + "'");
		}

		this.function = functionCatalog.lookup(functionName, "application/json");

		Set<String> names = functionCatalog.getNames(null);
		if (this.function == null && !CollectionUtils.isEmpty(names)) {

			if (logger.isInfoEnabled()) {
				if (names.size() == 1) {
					logger.info("Will default to RoutingFunction, since it is the only function available in FunctionCatalog."
							+ "Expecting 'spring.cloud.function.definition' or 'spring.cloud.function.routing-expression' as Message headers. "
							+ "If invocation is over API Gateway, Message headers can be provided as HTTP headers.");
				}
				else {
					logger.info("More then one function is available in FunctionCatalog. " + names
							+ " Will default to RoutingFunction, "
							+ "Expecting 'spring.cloud.function.definition' or 'spring.cloud.function.routing-expression' as Message headers. "
							+ "If invocation is over API Gateway, Message headers can be provided as HTTP headers.");
				}
			}
			this.function = functionCatalog.lookup(RoutingFunction.FUNCTION_NAME, "application/json");
		}

		if (this.function.isOutputTypePublisher()) {
			this.function.setSkipOutputConversion(true);
		}
		Assert.notNull(this.function, "Failed to lookup function " + functionName);

		if (!StringUtils.hasText(functionName)) {
			functionName = this.function.getFunctionDefinition();
		}

		if (logger.isInfoEnabled()) {
			logger.info("Located function: '" + functionName + "'");
		}
	}
}
