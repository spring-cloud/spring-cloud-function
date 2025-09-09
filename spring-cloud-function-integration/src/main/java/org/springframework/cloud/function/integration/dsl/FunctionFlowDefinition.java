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

package org.springframework.cloud.function.integration.dsl;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

/**
 * The {@link IntegrationFlowExtension} implementation for Spring Cloud Function domain.
 * Adds operators for functions and consumers and overloaded versions based on their names
 * or definitions resolved from the provided {@link org.springframework.cloud.function.context.FunctionCatalog}.
 *
 * @author Artem Bilan
 *
 * @since 4.0.3
 */
public final class FunctionFlowDefinition extends IntegrationFlowExtension<FunctionFlowDefinition> {

	private final FunctionLookupHelper functionLookupHelper;

	FunctionFlowDefinition(FunctionLookupHelper functionLookupHelper) {
		this.functionLookupHelper = functionLookupHelper;
	}

	MessageChannel getInputChannel() {
		return getCurrentMessageChannel();
	}

	void addUpstreamComponents(Map<Object, String> components) {
		addComponents(components);
	}

	/**
	 * Configure a {@link org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper}
	 * as a handler in the endpoint by its definition from the
	 * {@link org.springframework.cloud.function.context.FunctionCatalog}.
	 * @param functionDefinition the function definition in the function catalog.
	 * @return the current flow builder.
	 */
	public FunctionFlowDefinition apply(String functionDefinition) {
		return apply(this.functionLookupHelper.lookupFunction(functionDefinition));
	}

	/**
	 * Configure a {@link Function} as a handler in the endpoint.
	 * @param function the {@link Function} to use.
	 * @return the current flow builder.
	 */
	public FunctionFlowDefinition apply(Function<Message<?>, ?> function) {
		return handle(Message.class, (message, headers) -> function.apply(message));
	}

	/**
	 * Configure a {@link org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper}
	 * as a one-way handler in the final endpoint by its definition from the
	 * {@link org.springframework.cloud.function.context.FunctionCatalog}.
	 * @param consumerDefinition the consumer definition in the function catalog.
	 * @return the current flow builder.
	 */
	public IntegrationFlow accept(String consumerDefinition) {
		return accept(this.functionLookupHelper.lookupConsumer(consumerDefinition));
	}

	/**
	 * Configure a {@link Consumer} as a one-way handler in the final endpoint.
	 * @param consumer the {@link Consumer} to use.
	 * @return the current flow builder.
	 */
	public IntegrationFlow accept(Consumer<Message<?>> consumer) {
		return handle(consumer::accept)
				.get();
	}

}
