/*
 * Copyright 2019-2022 the original author or authors.
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
import java.util.Set;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.MapperFeature;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.utils.FunctionClassUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
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

	private JsonMapper jsonMapper;

	private FunctionInvocationWrapper function;

	private volatile String functionDefinition;

	public FunctionInvoker(String functionDefinition) {
		this.functionDefinition = functionDefinition;
		this.start();
	}

	public FunctionInvoker() {
		this(null);
	}

	@SuppressWarnings({ "rawtypes" })
	@Override
	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
		Message requestMessage = AWSLambdaUtils
				.generateMessage(input, this.function.getInputType(), this.function.isSupplier(), jsonMapper, context);

		Object response = this.function.apply(requestMessage);
		byte[] responseBytes = AWSLambdaUtils.generateOutputFromObject(requestMessage, response, this.jsonMapper, function.getOutputType());
		StreamUtils.copy(responseBytes, output);
		// any exception should propagate
	}

	private void start() {
		Class<?> startClass = FunctionClassUtils.getStartClass();
		String[] properties = new String[] {"--spring.cloud.function.web.export.enabled=false", "--spring.main.web-application-type=none"};
		ConfigurableApplicationContext context = ApplicationContextInitializer.class.isAssignableFrom(startClass)
				? FunctionalSpringApplication.run(new Class[] {startClass, AWSCompanionAutoConfiguration.class}, properties)
						: SpringApplication.run(new Class[] {startClass, AWSCompanionAutoConfiguration.class}, properties);

		Environment environment = context.getEnvironment();
		if (!StringUtils.hasText(this.functionDefinition)) {
			this.functionDefinition = environment.getProperty(FunctionProperties.FUNCTION_DEFINITION);
		}

		FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
		this.jsonMapper = context.getBean(JsonMapper.class);
		if (this.jsonMapper instanceof JacksonMapper) {
			((JacksonMapper) this.jsonMapper).configureObjectMapper(objectMapper -> {
				if (!objectMapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)) {
					objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
				}
			});
		}

		if (logger.isInfoEnabled()) {
			logger.info("Locating function: '" + this.functionDefinition + "'");
		}

		this.function = functionCatalog.lookup(this.functionDefinition, "application/json");

		if (this.function == null) {
			if (logger.isInfoEnabled()) {
				if (!StringUtils.hasText(this.functionDefinition)) {
					logger.info("Failed to determine default function. Please use 'spring.cloud.function.definition' property "
							+ "or pass function definition as a constructir argument to this FunctionInvoker");
				}
				Set<String> names = functionCatalog.getNames(null);
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
		Assert.notNull(this.function, "Failed to lookup function " + this.functionDefinition);

		this.functionDefinition = this.function.getFunctionDefinition();
		if (logger.isInfoEnabled()) {
			logger.info("Located function: '" + this.functionDefinition + "'");
		}
	}
}
