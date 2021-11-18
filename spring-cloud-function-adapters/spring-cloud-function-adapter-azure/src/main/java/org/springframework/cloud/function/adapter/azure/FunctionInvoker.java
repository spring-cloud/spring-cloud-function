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

package org.springframework.cloud.function.adapter.azure;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.cloud.function.context.config.JsonMessageConverter;
import org.springframework.cloud.function.context.config.SmartCompositeMessageConverter;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.utils.FunctionClassUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @param <I> input type
 * @param <O> result type
 * @author Oleg Zhurakousky
 * @since 3.2
 */
public class FunctionInvoker<I, O> {

	private static Log logger = LogFactory.getLog(FunctionInvoker.class);

	private static String EXECUTION_CONTEXT = "executionContext";

	private static FunctionCatalog FUNCTION_CATALOG;

	private static ConfigurableApplicationContext APPLICATION_CONTEXT;

	private static JsonMapper OBJECT_MAPPER;

	public FunctionInvoker(Class<?> configurationClass) {
		try {
			initialize(configurationClass);
		}
		catch (Exception e) {
			this.close();
			throw new IllegalStateException("Failed to initialize", e);
		}
	}

	public FunctionInvoker() {
		this(FunctionClassUtils.getStartClass());
	}

	public O handleRequest(ExecutionContext context) {
		return this.handleRequest(null, context);
	}

	public void close() {
		FUNCTION_CATALOG = null;
	}

	public void handleOutput(I input, OutputBinding<O> binding,
			ExecutionContext context) {
		O result = handleRequest(input, context);
		binding.setValue(result);
	}

	private FunctionInvocationWrapper discoverFunction(String functionDefinition) {
		FunctionInvocationWrapper function = FUNCTION_CATALOG.lookup(functionDefinition);
		if (function != null && StringUtils.hasText(functionDefinition) && !function.getFunctionDefinition().equals(functionDefinition)) {
			this.registerFunction(functionDefinition);
			function = FUNCTION_CATALOG.lookup(functionDefinition);
		}
		else if (function == null && StringUtils.hasText(functionDefinition) && APPLICATION_CONTEXT.containsBean(functionDefinition)) {
			this.registerFunction(functionDefinition);
			function = FUNCTION_CATALOG.lookup(functionDefinition);
		}
		return function;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public O handleRequest(I input, ExecutionContext executionContext) {
		String functionDefinition = executionContext.getFunctionName();
		FunctionInvocationWrapper function = this.discoverFunction(functionDefinition);
		Object enhancedInput = enhanceInputIfNecessary(input, executionContext);

		Object output = function.apply(enhancedInput);
		if (output instanceof Publisher) {
			if (FunctionTypeUtils.isMono(function.getOutputType())) {
				return (O) this.convertOutputIfNecessary(input, Mono.from((Publisher) output).blockOptional().get());
			}
			else {
				List resultList = new ArrayList<>();
				for (Object resultItem : Flux.from((Publisher) output).toIterable()) {
					if (resultItem instanceof Collection) {
						resultList.addAll((Collection) resultItem);
					}
					else {
						if (!function.isSupplier() && Collection.class.isAssignableFrom(FunctionTypeUtils.getRawType(function.getInputType()))
								&& !Collection.class.isAssignableFrom(FunctionTypeUtils.getRawType(function.getOutputType()))) {
							return (O) this.convertOutputIfNecessary(input, resultItem);
						}
						else {
							resultList.add(resultItem);
						}
					}
				}
				return (O) this.convertOutputIfNecessary(input, resultList);
			}
		}
		return (O) this.convertOutputIfNecessary(input, output);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void registerFunction(String functionDefinition) {
		if (APPLICATION_CONTEXT.containsBean(functionDefinition)) {
			FunctionRegistration functionRegistration =
					new FunctionRegistration(APPLICATION_CONTEXT.getBean(functionDefinition), functionDefinition);

			Type type = FunctionContextUtils.
					findType(functionDefinition, APPLICATION_CONTEXT.getBeanFactory());

			functionRegistration = functionRegistration.type(new FunctionType(type));

			((FunctionRegistry) FUNCTION_CATALOG).register(functionRegistration);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object enhanceInputIfNecessary(Object input, ExecutionContext executionContext) {
		if (input == null) { // Supplier
			return input;
		}
		if (input instanceof Publisher) {
			return Flux.from((Publisher) input).map(item -> {
				if (item instanceof Message) {
					return MessageBuilder.fromMessage((Message<I>) item)
							.setHeaderIfAbsent(EXECUTION_CONTEXT, executionContext).build();
				}
				else {
					return constructInputMessageFromItem(input, executionContext);
				}
			});
		}
		else if (input instanceof Message) {
			return MessageBuilder.fromMessage((Message<I>) input)
					.setHeaderIfAbsent(EXECUTION_CONTEXT, executionContext).build();
		}
		else if (input instanceof Iterable) {
			return Flux.fromIterable((Iterable) input).map(item -> {
				return constructInputMessageFromItem(item, executionContext);
			});
		}
		return constructInputMessageFromItem(input, executionContext);
	}

	@SuppressWarnings("unchecked")
	private Object convertOutputIfNecessary(Object input, Object output) {
		if (input instanceof HttpRequestMessage) {
			HttpRequestMessage<I> requestMessage = (HttpRequestMessage<I>) input;
			Map<String, Object> headers = null;
			if (output instanceof Message) {
				headers = ((Message<I>) output).getHeaders();
				output = ((Message<I>) output).getPayload();
			}
			Builder responseBuilder = requestMessage.createResponseBuilder(HttpStatus.OK).body(output);
			if (headers != null) {
				for (Entry<String, Object> headersEntry : headers.entrySet()) {
					if (headersEntry.getValue() != null) {
						responseBuilder.header(headersEntry.getKey(), headersEntry.getValue().toString());
					}
				}
			}
			return responseBuilder.build();
		}
		return output;
	}

	@SuppressWarnings("unchecked")
	private Message<?> constructInputMessageFromItem(Object input, ExecutionContext executionContext) {
		MessageBuilder<?> messageBuilder = null;
		if (input instanceof HttpRequestMessage) {
			HttpRequestMessage<I> requestMessage = (HttpRequestMessage<I>) input;
			Object payload = requestMessage.getHttpMethod() != null && requestMessage.getHttpMethod().equals(HttpMethod.GET)
					? requestMessage.getQueryParameters()
							: requestMessage.getBody();

			if (payload == null) {
				payload = Optional.empty();
			}
			messageBuilder = MessageBuilder.withPayload(payload).copyHeaders(this.getHeaders(requestMessage));
		}
		else {
			messageBuilder = MessageBuilder.withPayload(input);
		}
		return messageBuilder.setHeaderIfAbsent(EXECUTION_CONTEXT, executionContext).build();
	}

	private MessageHeaders getHeaders(HttpRequestMessage<I> event) {
		Map<String, Object> headers = new HashMap<String, Object>();

		if (event.getHeaders() != null) {
			headers.putAll(event.getHeaders());
		}
		if (event.getQueryParameters() != null) {
			headers.putAll(event.getQueryParameters());
		}
		if (event.getUri() != null) {
			headers.put("path", event.getUri().getPath());
		}

		if (event.getHttpMethod() != null) {
			headers.put("httpMethod", event.getHttpMethod().toString());
		}

		headers.put("request", event.getBody());
		return new MessageHeaders(headers);
	}

	private static void initialize(Class<?> configurationClass) {
		synchronized (FunctionInvoker.class.getName()) {
			if (FUNCTION_CATALOG == null) {
				logger.info("Initializing: " + configurationClass);
				SpringApplication builder = springApplication(configurationClass);
				APPLICATION_CONTEXT = builder.run();

				Map<String, FunctionCatalog> mf = APPLICATION_CONTEXT.getBeansOfType(FunctionCatalog.class);
				if (CollectionUtils.isEmpty(mf)) {
					OBJECT_MAPPER = new JacksonMapper(new ObjectMapper());
					JsonMessageConverter jsonConverter = new JsonMessageConverter(OBJECT_MAPPER);
					SmartCompositeMessageConverter messageConverter = new SmartCompositeMessageConverter(Collections.singletonList(jsonConverter));
					FUNCTION_CATALOG = new SimpleFunctionRegistry(APPLICATION_CONTEXT.getBeanFactory().getConversionService(),
							messageConverter, OBJECT_MAPPER);
				}
				else {
					OBJECT_MAPPER = APPLICATION_CONTEXT.getBean(JsonMapper.class);
					FUNCTION_CATALOG = mf.values().iterator().next();
				}
			}
		}

	}

	private static SpringApplication springApplication(Class<?> configurationClass) {
		SpringApplication application = new org.springframework.cloud.function.context.FunctionalSpringApplication(
				configurationClass);
		application.setWebApplicationType(WebApplicationType.NONE);
		return application;
	}
}
