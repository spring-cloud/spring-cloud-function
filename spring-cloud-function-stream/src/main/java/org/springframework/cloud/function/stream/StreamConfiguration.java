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

package org.springframework.cloud.function.stream;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.function.invoker.AbstractFunctionInvoker;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
@EnableConfigurationProperties(StreamConfigurationProperties.class)
@ConditionalOnClass({ Binder.class, AbstractFunctionInvoker.class })
@ConditionalOnProperty(name = "spring.cloud.stream.enabled", havingValue = "true", matchIfMissing = true)
public class StreamConfiguration {

	@ConditionalOnSupplier
	@EnableBinding(Source.class)
	protected static class SupplierConfiguration {

		@Autowired
		private StreamConfigurationProperties properties;

		@Bean
		@ConditionalOnProperty("spring.cloud.stream.bindings.output.destination")
		public SupplierInvokingMessageProducer<Object> invoker(FunctionCatalog registry) {
			String name = properties.getEndpoint();
			long interval = properties.getInterval();
			Supplier<Flux<Object>> supplier = registry.lookupSupplier(name);
			return new SupplierInvokingMessageProducer<Object>(supplier, interval);
		}
	}

	@ConditionalOnFunction
	@EnableBinding(Processor.class)
	protected static class FunctionConfiguration {

		@Autowired
		private StreamConfigurationProperties properties;

		@Bean
		@ConditionalOnProperty("spring.cloud.stream.bindings.input.destination")
		public AbstractFunctionInvoker<?, ?> invoker(FunctionCatalog registry, FunctionInspector functionInspector,
				@Lazy CompositeMessageConverterFactory compositeMessageConverterFactory) {
			String name = properties.getEndpoint();
			Function<Flux<?>, Flux<?>> function = registry.lookupFunction(name);
			Assert.notNull(function, "no such function: " + name);
			return new StreamListeningFunctionInvoker(name, function, functionInspector,
					compositeMessageConverterFactory);
		}
	}

	@ConditionalOnConsumer
	@EnableBinding(Sink.class)
	protected static class ConsumerConfiguration {

		@Autowired
		private StreamConfigurationProperties properties;

		@Bean
		@ConditionalOnProperty("spring.cloud.stream.bindings.input.destination")
		public StreamListeningConsumerInvoker invoker(FunctionCatalog registry, FunctionInspector functionInspector,
				@Lazy CompositeMessageConverterFactory compositeMessageConverterFactory) {
			String name = properties.getEndpoint();
			Consumer<Flux<?>> consumer = registry.lookupConsumer(name);
			return new StreamListeningConsumerInvoker(name, consumer, functionInspector,
					compositeMessageConverterFactory);
		}
	}

	@Conditional(SupplierCondition.class)
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	private @interface ConditionalOnSupplier {
	}

	@Conditional(FunctionCondition.class)
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	private @interface ConditionalOnFunction {
	}

	@Conditional(ConsumerCondition.class)
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	private @interface ConditionalOnConsumer {
	}

	private static abstract class AbstractFunctionCondition extends SpringBootCondition
			implements ConfigurationCondition {

		private final Class<?> type;

		private AbstractFunctionCondition(Class<?> type) {
			this.type = type;
		}

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String functionName = context.getEnvironment().getProperty("spring.cloud.function.stream.endpoint");
			if (!StringUtils.hasText(functionName)) {
				return ConditionOutcome.noMatch("no endpoint function name available");
			}
			if (functionName.indexOf(',') != -1) {
				// for now we will just check the first, but later may support:
				// supplier[,function]+ or [function,]+consumer
				functionName = functionName.substring(0, functionName.indexOf(','));
			}
			Class<?> beanType = context.getBeanFactory().getType(functionName);
			if (type.isAssignableFrom(beanType)) {
				return ConditionOutcome.match(String.format("bean '%s' is a %s", functionName, type));
			}
			return ConditionOutcome.noMatch(String.format("bean '%s' is not a %s", functionName, type));
		}

		@Override
		public ConfigurationPhase getConfigurationPhase() {
			return ConfigurationPhase.REGISTER_BEAN;
		}
	}

	private static class SupplierCondition extends AbstractFunctionCondition {

		public SupplierCondition() {
			super(Supplier.class);
		}
	}

	private static class FunctionCondition extends AbstractFunctionCondition {

		public FunctionCondition() {
			super(Function.class);
		}
	}

	private static class ConsumerCondition extends AbstractFunctionCondition {

		public ConsumerCondition() {
			super(Consumer.class);
		}
	}
}
