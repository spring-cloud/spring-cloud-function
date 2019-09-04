/*
 * Copyright 2019-2019 the original author or authors.
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

import java.lang.reflect.Type;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


/**
 * An implementation of Function which acts as a gateway/router by actually
 * delegating incoming invocation to a function specified .. .
 *
 * @author Oleg Zhurakousky
 * @since 2.1
 *
 */
public class RoutingFunction implements Function<Object, Object> {

	/**
	 * The name of this function use by BeanFactory.
	 */
	public static final String FUNCTION_NAME = "functionRouter";

	private static Log logger = LogFactory.getLog(RoutingFunction.class);

	private final StandardEvaluationContext evalContext = new StandardEvaluationContext();

	private final SpelExpressionParser spelParser = new SpelExpressionParser();

	private final FunctionCatalog functionCatalog;

	private final FunctionProperties functionProperties;

	private final FunctionInspector functionInspector;

	public RoutingFunction(FunctionCatalog functionCatalog, FunctionInspector functionInspector, FunctionProperties functionProperties) {
		this.functionCatalog = functionCatalog;
		this.functionProperties = functionProperties;
		this.functionInspector = functionInspector;
		this.evalContext.addPropertyAccessor(new MapAccessor());
	}

	@Override
	public Object apply(Object input) {
		return this.route(input, input instanceof Publisher);
	}

	/*
	 * - Check if function-name is set in header and if it is use it.
	 * If NOT
	 * - Check routing-expression and if it is set use it
	 * If NOT
	 * - Check function-name is set in FunctionProperties and if it is use it
	 * If NOT
	 * - Fail
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object route(Object input, boolean originalInputIsPublisher) {
		Function function;
		if (input instanceof Message) {
			Message<?> message = (Message<?>) input;
			if (StringUtils.hasText((String) message.getHeaders().get("spring.cloud.function.definition"))) {
				function = functionFromDefinition((String) message.getHeaders().get("spring.cloud.function.definition"));
				Type functionType = functionInspector.getRegistration(function).getType().getType();
				if (FunctionTypeUtils.isReactive(FunctionTypeUtils.getInputType(functionType, 0))) {
					this.assertOriginalInputIsNotPublisher(originalInputIsPublisher);
				}
			}
			else if (StringUtils.hasText((String) message.getHeaders().get("spring.cloud.function.routing-expression"))) {
				function = this.functionFromExpression((String) message.getHeaders().get("spring.cloud.function.routing-expression"), message);
				Type functionType = functionInspector.getRegistration(function).getType().getType();
				if (FunctionTypeUtils.isReactive(FunctionTypeUtils.getInputType(functionType, 0))) {
					this.assertOriginalInputIsNotPublisher(originalInputIsPublisher);
				}
			}
			else if (StringUtils.hasText(functionProperties.getRoutingExpression())) {
				function = this.functionFromExpression(functionProperties.getRoutingExpression(), message);
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
		else if (input instanceof Publisher) {
			if (StringUtils.hasText(functionProperties.getRoutingExpression())) {
				function = this.functionFromExpression(functionProperties.getRoutingExpression(), input);
			}
			else
			if (StringUtils.hasText(functionProperties.getDefinition())) {
				function = functionFromDefinition(functionProperties.getDefinition());
			}
			else {
				return input instanceof Mono
						? Mono.from((Publisher<?>) input).map(v -> route(v, originalInputIsPublisher))
								: Flux.from((Publisher<?>) input).map(v -> route(v, originalInputIsPublisher));
			}
		}
		else {
			this.assertOriginalInputIsNotPublisher(originalInputIsPublisher);
			if (StringUtils.hasText(functionProperties.getRoutingExpression())) {
				function = this.functionFromExpression(functionProperties.getRoutingExpression(), input);
			}
			else
			if (StringUtils.hasText(functionProperties.getDefinition())) {
				function = functionFromDefinition(functionProperties.getDefinition());
			}
			else {
				throw new IllegalStateException("Failed to establish route, since neither were provided: "
						+ "'spring.cloud.function.definition' as Message header or as application property or "
						+ "'spring.cloud.function.routing-expression' as application property.");
			}
		}

		return function.apply(input);
	}

	private void assertOriginalInputIsNotPublisher(boolean originalInputIsPublisher) {
		Assert.isTrue(!originalInputIsPublisher, "Routing input of type Publisher is not supported per individual "
				+ "values (e.g., message header or POJO). Instead you should use 'spring.cloud.function.definition' or "
				+ "spring.cloud.function.routing-expression' as application properties.");
	}

	@SuppressWarnings("rawtypes")
	private Function functionFromDefinition(String definition) {
		Function function = functionCatalog.lookup(definition);
		Assert.notNull(function, "Failed to lookup function to route based on the value of 'spring.cloud.function.definition' property '"
				+ functionProperties.getDefinition() + "'");
		if (logger.isInfoEnabled()) {
			logger.info("Resolved function from provided [definition] property " + functionProperties.getDefinition());
		}
		return function;
	}

	@SuppressWarnings("rawtypes")
	private Function functionFromExpression(String routingExpression, Object input) {
		Expression expression = spelParser.parseExpression(routingExpression);
		String functionName = expression.getValue(this.evalContext, input, String.class);
		Assert.hasText(functionName, "Failed to resolve function name based on routing expression '" + functionProperties.getRoutingExpression() + "'");
		Function function = functionCatalog.lookup(functionName);
		Assert.notNull(function, "Failed to lookup function to route to based on the expression '"
				+ functionProperties.getRoutingExpression() + "' whcih resolved to '" + functionName + "' function name.");
		if (logger.isInfoEnabled()) {
			logger.info("Resolved function from provided [routing-expression]  " + routingExpression);
		}
		return function;
	}
}
