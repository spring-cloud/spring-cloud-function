/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.function.observability;

import java.util.Objects;

import io.micrometer.observation.transport.SenderContext;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.messaging.support.MessageBuilder;

/**
 * {@link SenderContext} for sending messages through functional interfaces.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class FunctionSenderContext extends SenderContext<MessageBuilder<?>> {

	private final SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction;

	public FunctionSenderContext(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction, MessageBuilder<?> carrier) {
		super((messageBuilder, key, value) -> Objects.requireNonNull(messageBuilder).setHeader(key, value));
		this.targetFunction = targetFunction;
		setCarrier(carrier);
	}

	public SimpleFunctionRegistry.FunctionInvocationWrapper getTargetFunction() {
		return targetFunction;
	}

}
