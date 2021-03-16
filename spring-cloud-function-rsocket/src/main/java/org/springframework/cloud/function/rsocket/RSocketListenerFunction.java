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

package org.springframework.cloud.function.rsocket;

import java.util.Map;
import java.util.function.Function;

import io.rsocket.frame.FrameType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.rsocket.annotation.support.RSocketFrameTypeMessageCondition;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;

/**
 * A function wrapper which is bound onto an RSocket route.
 *
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 *
 * @since 3.1
 */
class RSocketListenerFunction implements Function<Object, Publisher<?>> {

	private final FunctionInvocationWrapper targetFunction;

	RSocketListenerFunction(FunctionInvocationWrapper targetFunction) {
		Assert.isTrue(targetFunction != null, "Failed to discover target function. \n"
				+ "To fix it you should either provide 'spring.cloud.function.definition' property "
				+ "or if you are using RSocketRequester provide valid function definition via 'route' "
				+ "operator (e.g., requester.route(\"echo\"))");
		this.targetFunction = targetFunction;
	}


	@SuppressWarnings("unchecked")
	@Override
	public Publisher<?> apply(Object input) {
		/*
		 * We need to maintain the input typeless to ensure that no encoder/decoders will attempt any conversion.
		 * That said it will always be Message<Publisher<Object>>
		 */
		Message<Publisher<Object>> inputMessage = (Message<Publisher<Object>>) input;

		FrameType frameType = RSocketFrameTypeMessageCondition.getFrameType(inputMessage);
		switch (frameType) {
			case REQUEST_FNF:
				return handle(inputMessage);
			case REQUEST_RESPONSE:
			case REQUEST_STREAM:
			case REQUEST_CHANNEL:
				return handleAndReply(inputMessage);
			default:
				throw new UnsupportedOperationException();
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Mono<Void> handle(Message<Publisher<Object>> messageToProcess) {
		if (this.targetFunction.isRoutingFunction()) {
			Flux<?> dataFlux = Flux.from(messageToProcess.getPayload())
					.map(payload -> MessageBuilder.createMessage(payload, messageToProcess.getHeaders()));
			return dataFlux.doOnNext(this.targetFunction).then();
		}
		else if (this.targetFunction.isConsumer()) {
			Flux<?> dataFlux = Flux.from(messageToProcess.getPayload())
					.map(payload -> this.buildReceivedMessage(payload, messageToProcess.getHeaders()));

			dataFlux = FunctionTypeUtils.isPublisher(this.targetFunction.getInputType())
					? dataFlux.transform((Function) this.targetFunction)
							: dataFlux.doOnNext(this.targetFunction);

			return dataFlux.then();
		}
		else {
			return Mono.error(new IllegalStateException("Only 'Consumer' can handle 'fire-and-forget' RSocket frame."));
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Flux<?> handleAndReply(Message<Publisher<Object>> messageToProcess) {
		Flux<?> dataFlux = Flux.from(messageToProcess.getPayload())
				.map(payload -> this.buildReceivedMessage(payload, messageToProcess.getHeaders()));

		if (this.targetFunction.getInputType() != null && FunctionTypeUtils.isPublisher(this.targetFunction.getInputType())) {
			dataFlux = dataFlux.transform((Function) this.targetFunction);
		}
		else {
			dataFlux = dataFlux.flatMap((data) -> {
				Map<String, ?> messageMap = FunctionRSocketUtils.sanitizeMessageToMap((Message<?>) data);
				Message sanitizedMessage = MessageBuilder.withPayload(messageMap.remove(FunctionRSocketUtils.PAYLOAD))
						.copyHeaders((Map<String, ?>) messageMap.get(FunctionRSocketUtils.HEADERS))
						.build();
				Object result = this.targetFunction.isSupplier() ? this.targetFunction.apply(null) : this.targetFunction.apply(sanitizedMessage);

				Publisher resultPublisher = result instanceof Publisher<?>
					? (Publisher<?>) result
					: Mono.just(result);
				return Flux.from(resultPublisher).map(v -> extractPayloadIfNecessary(v));
			});
		}
		return dataFlux;
	}

	private Message<?> buildReceivedMessage(Object mayBeMessage, MessageHeaders messageHeaders) {
		return mayBeMessage instanceof Message
				? MessageBuilder.fromMessage((Message<?>) mayBeMessage).copyHeadersIfAbsent(messageHeaders).build()
				: MessageBuilder.withPayload(mayBeMessage).copyHeadersIfAbsent(messageHeaders).build();
	}

	/*
	 * This will ensure that unless CT is application/json for which we provide Message aware encoder/decoder
	 * the payload is extracted since no other available encoders/decoders understand Message.
	 */
	private Object extractPayloadIfNecessary(Object output) {
		if (output instanceof Message) {
			Message<?> resultMessage = (Message<?>) output;
			Object contentType = resultMessage.getHeaders().get(MessageHeaders.CONTENT_TYPE);
			if (contentType != null && contentType.toString().equals(MimeTypeUtils.APPLICATION_JSON_VALUE)) {
				return output;
			}
			else {
				return resultMessage.getPayload();
			}
		}
		return output;
	}
}
