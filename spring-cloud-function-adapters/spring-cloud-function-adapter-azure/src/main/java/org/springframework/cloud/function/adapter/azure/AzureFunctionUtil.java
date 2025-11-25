/*
 * Copyright 2023-present the original author or authors.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

/**
 * @author Christian Tzolov
 * @author Oleg Zhurakousky
 * @author Chris Bono
 * @since 4.0
 */
public final class AzureFunctionUtil {

	/**
	 * Message header key name used to store and extract the ExecutionContext.
	 */
	public static String EXECUTION_CONTEXT = "executionContext";

	private AzureFunctionUtil() {
	};

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <I> Object enhanceInputIfNecessary(Object input, ExecutionContext executionContext) {
		if (input == null) { // Supplier
			return input;
		}
		if (input instanceof Publisher) {
			return Flux.from((Publisher) input).map(item -> {
				if (item instanceof Message) {
					return MessageBuilder.fromMessage((Message<I>) item)
						.setHeaderIfAbsent(EXECUTION_CONTEXT, executionContext)
						.build();
				}
				else {
					return constructInputMessageFromItem(input, executionContext);
				}
			});
		}
		else if (input instanceof Message) {
			return MessageBuilder.fromMessage((Message<I>) input)
				.setHeaderIfAbsent(EXECUTION_CONTEXT, executionContext)
				.build();
		}
		else if (input instanceof Iterable) {
			return Flux.fromIterable((Iterable) input).map(item -> {
				return constructInputMessageFromItem(item, executionContext);
			});
		}
		return constructInputMessageFromItem(input, executionContext);
	}

	private static <I> Message<?> constructInputMessageFromItem(Object input, ExecutionContext executionContext) {
		MessageBuilder<?> messageBuilder = null;
		if (input instanceof HttpRequestMessage) {
			HttpRequestMessage<I> requestMessage = (HttpRequestMessage<I>) input;
			Object payload = requestMessage.getHttpMethod() != null
					&& requestMessage.getHttpMethod().equals(HttpMethod.GET) ? requestMessage.getQueryParameters()
							: requestMessage.getBody();

			if (payload == null) {
				payload = Optional.empty();
			}
			messageBuilder = MessageBuilder.withPayload(payload).copyHeaders(getHeaders(requestMessage));
		}
		else {
			messageBuilder = MessageBuilder.withPayload(input);
		}
		return messageBuilder.setHeaderIfAbsent(EXECUTION_CONTEXT, executionContext).build();
	}

	private static <I> MessageHeaders getHeaders(HttpRequestMessage<I> event) {
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

}
