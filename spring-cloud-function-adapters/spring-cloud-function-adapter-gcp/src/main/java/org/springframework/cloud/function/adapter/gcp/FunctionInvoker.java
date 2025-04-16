/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.cloud.function.adapter.gcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.cloud.functions.Context;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.functions.RawBackgroundFunction;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.utils.FunctionClassUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;

/**
 * Implementation of {@link HttpFunction} and {@link RawBackgroundFunction} for Google
 * Cloud Function (GCF). This is the Spring Cloud Function adapter for GCF HTTP and Raw
 * Background function.
 *
 * @author Dmitry Solomakha
 * @author Mike Eltsufin
 * @author Oleg Zhurakousky
 * @author Biju Kunjummen
 * @since 3.0.4
 */
public class FunctionInvoker implements HttpFunction, RawBackgroundFunction {

	private static final Log log = LogFactory.getLog(FunctionInvoker.class);

	/**
	 * Constant specifying Http Status Code. Accessible to users by calling 'FunctionInvoker.HTTP_STATUS_CODE'
	 */
	public static final String HTTP_STATUS_CODE = "statusCode";

	private String functionName = "";

	protected FunctionCatalog catalog;

	private FunctionInvocationWrapper functionWrapped;

	private ConfigurableApplicationContext context;

	private JsonMapper jsonMapper;

	public FunctionInvoker() {
		this(FunctionClassUtils.getStartClass());
	}

	public FunctionInvoker(Class<?> configurationClass) {
		init(configurationClass);
	}

	private void init(Class<?> configurationClass) {
		// Default to GSON if implementation not specified.
		if (!System.getenv().containsKey(ContextFunctionCatalogAutoConfiguration.JSON_MAPPER_PROPERTY)) {
			System.setProperty(ContextFunctionCatalogAutoConfiguration.JSON_MAPPER_PROPERTY, "gson");
		}
		Thread.currentThread() // TODO: remove after upgrading to 1.0.0-alpha-2-rc5
				.setContextClassLoader(FunctionInvoker.class.getClassLoader());

		log.info("Initializing: " + configurationClass);
		SpringApplication springApplication = springApplication(configurationClass);
		this.context = springApplication.run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
		this.jsonMapper = this.context.getBean(JsonMapper.class);
		initFunctionConsumerOrSupplierFromCatalog();
	}

	private <I> Function<Message<I>, Message<byte[]>> lookupFunction() {
		Function<Message<I>, Message<byte[]>> function = this.catalog.lookup(functionName,
				MimeTypeUtils.APPLICATION_JSON.toString());
		Assert.notNull(function, "'function' with name '" + functionName + "' must not be null");
		return function;
	}

	/**
	 * The implementation of a GCF {@link HttpFunction} that will be used as the entry
	 * point from GCF.
	 */
	@Override
	public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
		Function<Message<BufferedReader>, Message<byte[]>> function = lookupFunction();

		Message<BufferedReader> message = this.functionWrapped.getInputType() == Void.class
				|| this.functionWrapped.getInputType() == null ? null
						: MessageBuilder.withPayload(httpRequest.getReader()).copyHeaders(httpRequest.getHeaders())
								.build();

		Object resultObject = function.apply(message);

		if (resultObject != null) {
			Message<?> result = resultObject instanceof Publisher<?> ? getResultFromPublisher(resultObject) : (Message<?>) resultObject;

			buildHttpResponse(httpRequest, httpResponse, result);
		}
	}

	/**
	 * The implementation of a GCF {@link RawBackgroundFunction} that will be used as the
	 * entry point from GCF.
	 *
	 * @param json    the payload.
	 * @param context event context.
	 * @since 3.0.5
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void accept(String json, Context context) {
		Function<Message<String>, Message<byte[]>> function = lookupFunction();
		Message<String> message = this.functionWrapped.getInputType() == Void.class ? null
				: MessageBuilder.withPayload(json).setHeader("gcf_context", context).build();

		Object resultObject = function.apply(message);

		Message<byte[]> result = null;
		if (resultObject instanceof Publisher<?>) {
			result = getResultFromPublisher(resultObject);
		}
		else {
			result = (Message<byte[]>) resultObject;
		}

		if (result != null) {
			log.info("Dropping background function result: " + new String(result.getPayload()));
		}
	}

	/*
	 * This method build the http response from service.
	 */
	private void buildHttpResponse(HttpRequest httpRequest, HttpResponse httpResponse, Message<?> result)
			throws IOException {
		MessageHeaders headers = result.getHeaders();
		if (result.getHeaders().containsKey(MessageHeaders.CONTENT_TYPE)) {
			httpResponse.setContentType(result.getHeaders().get(MessageHeaders.CONTENT_TYPE).toString());
		}
		else if (result.getHeaders().containsKey("Content-Type")) {
			httpResponse.setContentType(result.getHeaders().get("Content-Type").toString());
		}
		else {
			httpRequest.getContentType().ifPresent(contentType -> httpResponse.setContentType(contentType));
		}
		String content = result.getPayload() instanceof String strPayload ? strPayload
				: new String((byte[]) result.getPayload(), StandardCharsets.UTF_8);
		httpResponse.getWriter().write(content);
		for (Entry<String, Object> header : headers.entrySet()) {
			Object values = header.getValue();
			if (values instanceof Collection<?>) {
				String headerValue = ((Collection<?>) values).stream().map(item -> item.toString())
						.collect(Collectors.joining(","));
				httpResponse.appendHeader(header.getKey(), headerValue);
			}
			else {
				httpResponse.appendHeader(header.getKey(), header.getValue().toString());
			}
		}

		if (headers.containsKey(HTTP_STATUS_CODE)) {
			if (headers.get(HTTP_STATUS_CODE) instanceof Integer) {
				httpResponse.setStatusCode((int) headers.get(HTTP_STATUS_CODE));
			}
			else {
				log.warn("The statusCode should be an Integer value");
			}
		}
	}

	/*
	 * This methd get the result from reactor's publisher.
	 *
	 * For reference: https://github.com/spring-cloud/spring-cloud-function/blob/main/spring-cloud-function-adapters/spring-cloud-function-adapter-aws/src/main/java/org/springframework/cloud/function/adapter/aws/AWSLambdaUtils.java
	 */
	private Message<byte[]> getResultFromPublisher(Object resultObject) {
		List<Object> results = new ArrayList<>();
		Message<?> lastMessage = null;
		for (Object item : Flux.from((Publisher<?>) resultObject).toIterable()) {
			log.info("Response value: " + item);
			if (item instanceof Message<?> messageItem) {
				results.add(convertFromJsonIfNecessary(messageItem.getPayload()));
				lastMessage = messageItem;
			}
			else {
				results.add(convertFromJsonIfNecessary(item));
			}
		}

		byte[] resultsPayload;
		if (results.size() == 1) {
			resultsPayload = jsonMapper.toJson(results.get(0));
		}
		else if (results.size() > 1) {
			resultsPayload = jsonMapper.toJson(results);
		}
		else {
			resultsPayload = null;
		}

		Assert.notNull(resultsPayload, "Couldn't resolve payload result");

		MessageBuilder<byte[]> messageBuilder = MessageBuilder.withPayload(resultsPayload);
		if (lastMessage != null) {
			messageBuilder.copyHeaders(lastMessage.getHeaders());
		}
		return messageBuilder.build();
	}

	private Object convertFromJsonIfNecessary(Object value) {
		if (JsonMapper.isJsonString(value)) {
			return jsonMapper.fromJson(value, Object.class);
		}

		return value;
	}

	private void initFunctionConsumerOrSupplierFromCatalog() {
		String name = resolveName(Function.class);
		this.functionWrapped = this.catalog.lookup(Function.class, name);
		if (this.functionWrapped != null) {
			this.functionName = name;
			return;
		}
		name = resolveName(Consumer.class);
		this.functionWrapped = this.catalog.lookup(Consumer.class, name);
		if (this.functionWrapped != null) {
			this.functionName = name;
			return;
		}

		name = resolveName(Supplier.class);
		this.functionWrapped = this.catalog.lookup(Supplier.class, name);
		if (this.functionWrapped != null) {
			this.functionName = name;
			return;
		}

		// Default to Routing Function
		this.functionWrapped = this.catalog.lookup(RoutingFunction.FUNCTION_NAME, "application/json");
		if (this.functionWrapped != null) {
			this.functionName = RoutingFunction.FUNCTION_NAME;
		}

		Assert.notNull(this.functionWrapped, "Couldn't resolve a handler function");
	}

	private String resolveName(Class<?> type) {
		if (System.getenv().containsKey("spring.cloud.function.definition")) {
			return System.getenv("spring.cloud.function.definition");
		}
		String functionName = this.context.getEnvironment().getProperty("function.name");
		if (functionName != null) {
			return functionName;
		}
		else if (type.isAssignableFrom(Function.class)) {
			return "function";
		}
		else if (type.isAssignableFrom(Consumer.class)) {
			return "consumer";
		}
		else if (type.isAssignableFrom(Supplier.class)) {
			return "supplier";
		}
		throw new IllegalStateException("Unknown type " + type);
	}

	private SpringApplication springApplication(Class<?> configurationClass) {
		SpringApplication application = new FunctionalSpringApplication(configurationClass);
		application.setWebApplicationType(WebApplicationType.NONE);
		return application;
	}

}
