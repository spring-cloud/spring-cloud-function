/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.stream.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
@Configuration
@EnableConfigurationProperties(StreamConfigurationProperties.class)
@ConditionalOnClass(Binder.class)
@ConditionalOnBean(FunctionCatalog.class)
@ConditionalOnProperty(name = "spring.cloud.stream.enabled", havingValue = "true", matchIfMissing = true)
public class StreamAutoConfiguration {

	@Configuration
	// Because of the underlying behaviour of Spring AMQP etc., sources do not start
	// up and fail gracefully if the broker is down. So we need a flag to be able to
	// switch this off and stop the app failing on startup.
	@ConditionalOnProperty(name = "spring.cloud.function.stream.source.enabled", havingValue = "true", matchIfMissing = true)
	protected static class SourceConfiguration {

		@Autowired
		private StreamConfigurationProperties properties;

		@Bean
		public SupplierInvokingMessageProducer<Object> supplierInvoker(
				FunctionCatalog registry) {
			return new SupplierInvokingMessageProducer<Object>(registry,
					properties.getSource().getName());
		}

	}

	@Configuration
	@ConditionalOnProperty(name = "spring.cloud.function.stream.processor.enabled", havingValue = "true", matchIfMissing = true)
	@Conditional(SourceAndSinkCondition.class)
	protected static class ProcessorConfiguration {

		@Autowired
		private StreamConfigurationProperties properties;

		@Bean
		public StreamListeningFunctionInvoker functionInvoker(FunctionCatalog registry,
				FunctionInspector functionInspector,
				@Lazy CompositeMessageConverterFactory compositeMessageConverterFactory) {
			return new StreamListeningFunctionInvoker(registry, functionInspector,
					compositeMessageConverterFactory, properties.getDefaultRoute(),
					properties.isShared());
		}

	}

	@Configuration
	@Conditional(SinkOnlyCondition.class)
	protected static class SinkConfiguration {

		@Autowired
		private StreamConfigurationProperties properties;

		public SinkConfiguration() {
		}

		@Bean
		public StreamListeningConsumerInvoker consumerInvoker(FunctionCatalog registry,
				FunctionInspector functionInspector,
				@Lazy CompositeMessageConverterFactory compositeMessageConverterFactory) {
			return new StreamListeningConsumerInvoker(registry, functionInspector,
					compositeMessageConverterFactory, properties.getSink().getName(),
					properties.isShared());
		}

	}

	@Configuration
	@EnableBinding(Processor.class)
	@Conditional(ProcessorCondition.class)
	protected class ProcessorBindingConfiguration {
	}

	@Configuration
	@EnableBinding(Source.class)
	@Conditional(SourceCondition.class)
	protected class SourceBindingConfiguration {
	}

	@Configuration
	@EnableBinding(Sink.class)
	@Conditional(SinkCondition.class)
	protected class SinkBindingConfiguration {
	}

	private static class SinkOnlyCondition extends SpringBootCondition {
		private SourceAndSinkCondition processor = new SourceAndSinkCondition();

		private SinkCondition sink = new SinkCondition();

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context,
				AnnotatedTypeMetadata metadata) {
			if (processor.matches(context, metadata)) {
				return ConditionOutcome.noMatch("Source is provided by Processor");
			}
			if (sink.matches(context, metadata)) {
				return ConditionOutcome.match("Sink is explicitly enabled");
			}
			return ConditionOutcome
					.noMatch("Sink is not enabled and not available through Processor");
		}
	}

	private static class SourceAndSinkCondition extends SpringBootCondition {
		private SourceCondition source = new SourceCondition();

		private SinkCondition sink = new SinkCondition();

		private ProcessorCondition processor = new ProcessorCondition();

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context,
				AnnotatedTypeMetadata metadata) {
			if (processor.matches(context, metadata)) {
				return ConditionOutcome.match("Processor is bound");
			}
			if (sink.matches(context, metadata) && source.matches(context, metadata)) {
				return ConditionOutcome.match("Both Source and Sink are bound");
			}
			return ConditionOutcome.noMatch("Both Source and Sink are not bound");
		}
	}

	private static class ProcessorCondition extends SpringBootCondition {

		private SourceCondition source = new SourceCondition();

		private SinkCondition sink = new SinkCondition();

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context,
				AnnotatedTypeMetadata metadata) {
			return (!source.matches(context, metadata)
					&& !sink.matches(context, metadata))
							? ConditionOutcome.match(
									"Neither source nor sink is explicitly disabled")
							: ConditionOutcome.noMatch(
									"Either sink or source was explicitly disabled");
		}

	}

	private static class SourceCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context,
				AnnotatedTypeMetadata metadata) {
			Boolean enabled = context.getEnvironment().getProperty(
					"spring.cloud.function.stream.source.enabled", Boolean.class);
			Boolean sink = context.getEnvironment().getProperty(
					"spring.cloud.function.stream.sink.enabled", Boolean.class, true);
			if (enabled != null && enabled) {
				if (!sink) {
					return ConditionOutcome
							.match("Source explicitly enabled and sink disabled");
				}
				else {
					return ConditionOutcome
							.noMatch("Source explicitly enabled and sink enabled");
				}
			}
			if (enabled == null) {
				if (!sink) {
					return ConditionOutcome
							.match("Source implicitly enabled and sink disabled");
				}
				else {
					return ConditionOutcome
							.noMatch("Source not explicitly enabled and sink enabled");
				}
			}
			return ConditionOutcome.noMatch("Source explicitly disabled");
		}

	}

	private static class SinkCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context,
				AnnotatedTypeMetadata metadata) {
			Boolean enabled = context.getEnvironment().getProperty(
					"spring.cloud.function.stream.sink.enabled", Boolean.class);
			Boolean source = context.getEnvironment().getProperty(
					"spring.cloud.function.stream.source.enabled", Boolean.class, true);
			if (enabled != null && enabled) {
				if (!source) {
					return ConditionOutcome
							.match("Sink explicitly enabled and source disabled");
				}
				else {
					return ConditionOutcome
							.noMatch("Sink explicitly enabled and source enabled");
				}
			}
			if (enabled == null) {
				if (!source) {
					return ConditionOutcome
							.match("Sink implicitly enabled and source disabled");
				}
				else {
					return ConditionOutcome
							.noMatch("Sink not explicitly enabled and source enabled");
				}
			}
			return ConditionOutcome.noMatch("Sink explicitly disabled");
		}

	}

}
