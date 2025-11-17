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

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.GatewayProxySpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowBuilder;
import org.springframework.integration.dsl.MessageChannelSpec;
import org.springframework.integration.dsl.MessageProducerSpec;
import org.springframework.integration.dsl.MessageSourceSpec;
import org.springframework.integration.dsl.MessagingGatewaySpec;
import org.springframework.integration.dsl.SourcePollingChannelAdapterSpec;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.gateway.MessagingGatewaySupport;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.Assert;

/**
 * The entry point for starting a {@link FunctionFlowDefinition}. Requires a
 * {@link FunctionCatalog} to lookup function instances by their names or definitions from
 * respective operators.
 * <p>
 * In addition to standard {@link IntegrationFlow} {@code from()} overloaded methods (for
 * convenience), this class introduces {@link #fromSupplier(String)} factory methods to
 * resolve the target {@link Supplier} by its name or function definition from the
 * provided {@link FunctionCatalog}.
 * <p>
 * This class represents a DSL for functions composition via integration endpoints. Extra
 * processing can be done in between functions by the regular {@link IntegrationFlow}
 * operators: <pre class="code">
 * {@code
 * &#64;Bean
 * IntegrationFlow someFunctionFlow(FunctionFlowBuilder functionFlowBuilder) {
 *		return functionFlowBuilder
 *				.fromSupplier("timeSupplier")
 *				.apply("spelFunction")
 *				.log(LoggingHandler.Level.DEBUG, "some.log.category")
 *				.<String, String>transform(String::toUpperCase)
 *				.accept("fileConsumer");
 * }
 * }
 * </pre>
 *
 * @author Artem Bilan
 * @since 4.0.3
 */
public class FunctionFlowBuilder {

	private final FunctionLookupHelper functionLookupHelper;

	public FunctionFlowBuilder(FunctionCatalog functionCatalog) {
		Assert.notNull(functionCatalog, "'functionCatalog' must not be null");
		this.functionLookupHelper = new FunctionLookupHelper(functionCatalog);
	}

	public FunctionFlowDefinition fromSupplier(String supplierDefinition) {
		return fromSupplier(supplierDefinition, null);
	}

	public FunctionFlowDefinition fromSupplier(String supplierDefinition,
			@Nullable Consumer<SourcePollingChannelAdapterSpec> endpointConfigurer) {

		return fromSupplier(this.functionLookupHelper.lookupSupplier(supplierDefinition), endpointConfigurer);
	}

	public <T> FunctionFlowDefinition fromSupplier(Supplier<T> messageSource) {
		return fromSupplier(messageSource, null);
	}

	public <T> FunctionFlowDefinition fromSupplier(Supplier<T> messageSource,
			@Nullable Consumer<SourcePollingChannelAdapterSpec> endpointConfigurer) {

		return toFunctionFlow(IntegrationFlow.fromSupplier(messageSource, endpointConfigurer));
	}

	public FunctionFlowDefinition from(MessageChannel messageChannel) {
		return toFunctionFlow(IntegrationFlow.from(messageChannel));
	}

	public FunctionFlowDefinition from(String messageChannelName) {
		return from(messageChannelName, false);
	}

	public FunctionFlowDefinition from(String messageChannelName, boolean fixedSubscriber) {
		return toFunctionFlow(IntegrationFlow.from(messageChannelName, fixedSubscriber));
	}

	public FunctionFlowDefinition from(MessageSourceSpec<?, ? extends MessageSource<?>> messageSourceSpec,
			Consumer<SourcePollingChannelAdapterSpec> endpointConfigurer) {

		return toFunctionFlow(IntegrationFlow.from(messageSourceSpec, endpointConfigurer));
	}

	public FunctionFlowDefinition from(MessageSource<?> messageSource) {
		return from(messageSource, null);
	}

	public FunctionFlowDefinition from(MessageSource<?> messageSource,
			@Nullable Consumer<SourcePollingChannelAdapterSpec> endpointConfigurer) {

		return toFunctionFlow(IntegrationFlow.from(messageSource, endpointConfigurer));
	}

	public FunctionFlowDefinition from(MessageProducerSupport messageProducer) {
		return toFunctionFlow(IntegrationFlow.from(messageProducer));
	}

	public FunctionFlowDefinition from(MessagingGatewaySupport inboundGateway) {
		return toFunctionFlow(IntegrationFlow.from(inboundGateway));
	}

	public FunctionFlowDefinition from(MessageChannelSpec<?, ?> messageChannelSpec) {
		return toFunctionFlow(IntegrationFlow.from(messageChannelSpec));
	}

	public FunctionFlowDefinition from(MessageProducerSpec<?, ?> messageProducerSpec) {
		return toFunctionFlow(IntegrationFlow.from(messageProducerSpec));
	}

	public FunctionFlowDefinition from(MessageSourceSpec<?, ? extends MessageSource<?>> messageSourceSpec) {
		return toFunctionFlow(IntegrationFlow.from(messageSourceSpec));
	}

	public FunctionFlowDefinition from(MessagingGatewaySpec<?, ?> inboundGatewaySpec) {
		return toFunctionFlow(IntegrationFlow.from(inboundGatewaySpec));
	}

	public FunctionFlowDefinition from(Class<?> serviceInterface) {
		return from(serviceInterface, null);
	}

	public FunctionFlowDefinition from(Class<?> serviceInterface,
			@Nullable Consumer<GatewayProxySpec> endpointConfigurer) {

		return toFunctionFlow(IntegrationFlow.from(serviceInterface, endpointConfigurer));
	}

	public FunctionFlowDefinition from(Publisher<? extends Message<?>> publisher) {
		return toFunctionFlow(IntegrationFlow.from(publisher));
	}

	private FunctionFlowDefinition toFunctionFlow(IntegrationFlowBuilder from) {
		FunctionFlowDefinition functionFlow = new FunctionFlowDefinition(this.functionLookupHelper);
		from.channel(functionFlow.getInputChannel());
		functionFlow.addUpstreamComponents(from.get().getIntegrationComponents());
		return functionFlow;
	}

}
