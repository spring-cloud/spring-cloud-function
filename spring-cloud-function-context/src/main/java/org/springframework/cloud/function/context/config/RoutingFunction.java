/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * An implementation of {@link Function} which acts as a gateway/router by actually
 * delegating incoming invocation to a function specified .. .
 *
 * @author Oleg Zhurakousky
 * @author John Blum
 * @since 2.1
 *
 */
// TODO - perhaps change to Function<Message<Object>, Message<Object>>
public class RoutingFunction implements Function<Object, Object> {

	/**
	 * The name of this function use by BeanFactory.
	 */
	public static final String FUNCTION_NAME = "functionRouter";

	/**
	 * The name of this function for routing of un-routable messages.
	 */
	public static final String DEFAULT_ROUTE_HANDLER = "defaultMessageRoutingHandler";

	private static Log logger = LogFactory.getLog(RoutingFunction.class);

	private final StandardEvaluationContext evalContext = new StandardEvaluationContext();

	private final SimpleEvaluationContext headerEvalContext = SimpleEvaluationContext
		.forPropertyAccessors(DataBindingPropertyAccessor.forReadOnlyAccess())
		.build();

	private final SpelExpressionParser spelParser = new SpelExpressionParser();

	private final FunctionCatalog functionCatalog;

	private final FunctionProperties functionProperties;

	private final MessageRoutingCallback routingCallback;

	public RoutingFunction(FunctionCatalog functionCatalog, FunctionProperties functionProperties) {
		this(functionCatalog, functionProperties, null, null);
	}

	public RoutingFunction(FunctionCatalog functionCatalog, Map<String, String> propertiesMap,
			BeanResolver beanResolver, MessageRoutingCallback routingCallback) {
		this(functionCatalog, extractIntoFunctionProperties(propertiesMap), beanResolver, routingCallback);
	}

	public RoutingFunction(FunctionCatalog functionCatalog, FunctionProperties functionProperties,
			BeanResolver beanResolver, MessageRoutingCallback routingCallback) {
		this.functionCatalog = functionCatalog;
		this.functionProperties = functionProperties;
		this.routingCallback = routingCallback;
		this.evalContext.addPropertyAccessor(new MapAccessor());
		evalContext.setBeanResolver(beanResolver);
	}

	private static FunctionProperties extractIntoFunctionProperties(Map<String, String> propertiesMap) {
		FunctionProperties functionProperties = new FunctionProperties();
		functionProperties.setDefinition(propertiesMap.get(FunctionProperties.FUNCTION_DEFINITION));
		functionProperties.setRoutingExpression(propertiesMap.get(FunctionProperties.ROUTING_EXPRESSION));
		return functionProperties;
	}

	@Override
	public Object apply(Object input) {
		return this.route(input, input instanceof Publisher);
	}

	/*
	 * - Check if `this.routingCallback` is present and if it is use it (only for Message
	 * input) If NOT - Check if spring.cloud.function.definition is set in header and if
	 * it is use it.(only for Message input) If NOT - Check if
	 * spring.cloud.function.routing-expression is set in header and if it is set use it
	 * (only for Message input) If NOT - Check `spring.cloud.function.definition` is set
	 * in FunctionProperties and if it is use it (Message and Publisher) If NOT - Check
	 * `spring.cloud.function.routing-expression` is set in FunctionProperties and if it
	 * is use it (Message and Publisher) If NOT - Fail
	 */
	private Object route(Object input, boolean originalInputIsPublisher) {
		FunctionInvocationWrapper function = null;

		if (input instanceof Message<?> message) {
			if (this.routingCallback != null) {
				String functionDefinition = this.routingCallback.routingResult(message);
				if (StringUtils.hasText(functionDefinition)) {
					function = this.functionFromDefinition(functionDefinition);
				}
			}
			if (function == null) {
				function = this.locateFunctionFromDefinitionOrExpression(message);
				if (function != null) {
					if (function.isInputTypePublisher()) {
						this.assertOriginalInputIsNotPublisher(originalInputIsPublisher);
					}
				}
				else if (StringUtils.hasText(functionProperties.getRoutingExpression())) {
					function = this.functionFromExpression(functionProperties.getRoutingExpression(), message);
				}
				else if (StringUtils.hasText(functionProperties.getDefinition())) {
					function = this.functionFromDefinition(functionProperties.getDefinition());
				}
				else {
					throw new IllegalStateException("Failed to establish route, since neither were provided: "
							+ "'spring.cloud.function.definition' as Message header or as application property or "
							+ "'spring.cloud.function.routing-expression' as application property. Incoming message: "
							+ input);
				}
			}
		}
		else if (input instanceof Publisher<?> publisher) {
			if (StringUtils.hasText(functionProperties.getDefinition())) {
				function = functionFromDefinition(functionProperties.getDefinition());
			}
			else if (StringUtils.hasText(functionProperties.getRoutingExpression())) {
				function = this.functionFromExpression(functionProperties.getRoutingExpression(), input);
			}
			else {
				return input instanceof Mono<?> mono ? Mono.from(mono).map(v -> route(v, originalInputIsPublisher))
						: Flux.from(publisher).map(v -> route(v, originalInputIsPublisher));
			}
		}
		else {
			this.assertOriginalInputIsNotPublisher(originalInputIsPublisher);
			if (StringUtils.hasText(functionProperties.getRoutingExpression())) {
				function = this.functionFromExpression(functionProperties.getRoutingExpression(), input);
			}
			else if (StringUtils.hasText(functionProperties.getDefinition())) {
				function = functionFromDefinition(functionProperties.getDefinition());
			}
			else {
				throw new IllegalStateException("Failed to establish route, since neither were provided: "
						+ "'spring.cloud.function.definition' as Message header or as application property or "
						+ "'spring.cloud.function.routing-expression' as application property.");
			}
		}

		if (this.equals(function.getTarget())) {
			throw new IllegalStateException(
					"Failed to establish route, and routing to itself is not allowed as it creates a loop. Please provide: "
							+ "'spring.cloud.function.definition' as Message header or as application property or "
							+ "'spring.cloud.function.routing-expression' as application property.");
		}

		return function.apply(input);
	}

	private FunctionInvocationWrapper locateFunctionFromDefinitionOrExpression(Message<?> message) {
		for (Entry<String, Object> headerEntry : message.getHeaders().entrySet()) {
			String headerKey = headerEntry.getKey();
			Object headerValue = headerEntry.getValue();

			if (headerKey == null || headerValue == null) {
				continue;
			}

			boolean isFunctionDefinition = FunctionProperties.FUNCTION_DEFINITION.equalsIgnoreCase(headerKey);
			boolean isRoutingExpression = FunctionProperties.ROUTING_EXPRESSION.equalsIgnoreCase(headerKey);

			if (isFunctionDefinition) {
				if (headerValue instanceof String definition) {
					return functionFromDefinition(definition);
				}
				else if (headerValue instanceof List<?> definitions && !definitions.isEmpty()) {
					return functionFromDefinition(
							definitions.stream().map(Object::toString).collect(Collectors.joining(",")));
				}
			}
			else if (isRoutingExpression) {
				if (headerValue instanceof String expression) {
					return functionFromExpression(expression, message, true);
				}
				else if (headerValue instanceof List<?> expressions && !expressions.isEmpty()) {
					return functionFromExpression(expressions.get(0).toString(), message, true);
				}
			}
		}
		return null;
	}

	private void assertOriginalInputIsNotPublisher(boolean originalInputIsPublisher) {
		Assert.isTrue(!originalInputIsPublisher, "Routing input of type Publisher is not supported per individual "
				+ "values (e.g., message header or POJO). Instead you should use 'spring.cloud.function.definition' or "
				+ "spring.cloud.function.routing-expression' as application properties.");
	}

	private FunctionInvocationWrapper functionFromDefinition(String definition) {
		FunctionInvocationWrapper function = this.resolveFunction(definition);
		Assert.notNull(function,
				"Failed to lookup function to route based on the value of 'spring.cloud.function.definition' property '"
						+ definition + "'");
		if (logger.isDebugEnabled()) {
			logger.debug("Resolved function from provided [definition] property " + definition);
		}
		return function;
	}

	private FunctionInvocationWrapper functionFromExpression(String routingExpression, Object input) {
		return functionFromExpression(routingExpression, input, false);
	}

	private FunctionInvocationWrapper functionFromExpression(String routingExpression, Object input,
			boolean isViaHeader) {
		Expression expression = spelParser.parseExpression(routingExpression);
		if (input instanceof Message) {
			input = MessageUtils.toCaseInsensitiveHeadersStructure((Message<?>) input);
		}

		String definition = isViaHeader ? expression.getValue(this.headerEvalContext, input, String.class)
				: expression.getValue(this.evalContext, input, String.class);
		Assert.hasText(definition, "Failed to resolve function name based on routing expression '"
				+ functionProperties.getRoutingExpression() + "'");
		FunctionInvocationWrapper function = this.resolveFunction(definition);
		Assert.notNull(function,
				"Failed to lookup function to route to based on the expression '"
						+ functionProperties.getRoutingExpression() + "' which resolved to '" + definition
						+ "' function definition.");
		if (logger.isDebugEnabled()) {
			logger.debug("Resolved function from provided [routing-expression]  " + routingExpression);
		}
		return function;
	}

	private FunctionInvocationWrapper resolveFunction(String definition) {
		FunctionInvocationWrapper function = functionCatalog.lookup(definition);
		if (function == null) {
			function = functionCatalog.lookup(RoutingFunction.DEFAULT_ROUTE_HANDLER);
		}
		return function;
	}

}
