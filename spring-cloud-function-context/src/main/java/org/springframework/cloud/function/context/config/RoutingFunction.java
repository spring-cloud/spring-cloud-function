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

import java.util.function.Function;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;

/**
 * An implementation of Function which acts as a gateway/router by actually
 * delegating incoming invocation to a function specified via `function.name`
 * message header. <br>
 * {@link Message} is used as a canonical representation of a request which
 * contains some metadata and it is the responsibility of the higher level
 * framework to convert the incoming request into a Message. For example;
 * spring-cloud-function-web will create Message from HttpRequest copying all
 * HTTP headers as message headers.
 *
 * @author Oleg Zhurakousky
 * @since 2.1
 *
 */
public class RoutingFunction implements Function<Publisher<Message<?>>, Publisher<?>> {

	/**
	 * The name of this function use by BeanFactory.
	 */
	public static final String FUNCTION_NAME = "router";

	private final FunctionCatalog functionCatalog;

	private final FunctionInspector functionInspector;

	private final MessageConverter messageConverter;

	RoutingFunction(FunctionCatalog functionCatalog, FunctionInspector functionInspector,
			MessageConverter messageConverter) {
		this.functionCatalog = functionCatalog;
		this.functionInspector = functionInspector;
		this.messageConverter = messageConverter;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Publisher<?> apply(Publisher<Message<?>> input) {
		return Flux.from(input)
				.switchOnFirst((signal, flux) -> {
					Assert.isTrue(signal.hasValue()
							&& signal.getType() == SignalType.ON_NEXT, "Signal has no value or wrong type " + signal);
					Function<Flux<Object>, Publisher<Object>> function = this.getRouteToFunction(signal.get());
					return flux.map(message -> {
						Object inputValue = this.convertInput(message, function);
						return inputValue;
					})
					.log()
					.doOnError(error -> {
						throw new IllegalStateException("Failed to convert Message. Possible reason; "
								+ "No suitable converter was found for payload with 'contentType' "
								+ signal.get().getHeaders().get(MessageHeaders.CONTENT_TYPE), error);
					})
					.transform(function);
		});
	}

	@SuppressWarnings("rawtypes")
	private Function getRouteToFunction(Message<?> message) {
		String routeToFunctionName = (String) message.getHeaders().get("function.name");
		Assert.hasText(routeToFunctionName, "A 'function.name' was not provided as message header.");
		Function function = functionCatalog.lookup(routeToFunctionName);
		Assert.notNull(function, "Failed to locate function specified with 'function.name':"
				+ message.getHeaders().get("function.name"));
		return function;
	}

	private Object convertInput(Message<?> message, Object function) {
		Class<?> inputType = functionInspector.getInputType(function);
		Object inputValue = message.getPayload();
		if (!inputValue.getClass().isAssignableFrom(inputType)) {
			inputValue = this.messageConverter.fromMessage(message, functionInspector.getInputType(function));
		}
		if (this.functionInspector.isMessage(function)) {
			inputValue = MessageBuilder.createMessage(inputValue, message.getHeaders());
		}
		Assert.notNull(inputValue, "Failed to determine input value of type "
				+ inputType + " from Message '"
				+ message + "'. No suitable Message Converter found.");
		return inputValue;
	}
}
