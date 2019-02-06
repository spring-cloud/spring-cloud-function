/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.function.context.AbstractFunctionRegistry;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.catalog.FunctionRegistrationEvent;
import org.springframework.cloud.function.context.catalog.FunctionUnregistrationEvent;
import org.springframework.cloud.function.core.FluxConsumer;
import org.springframework.cloud.function.core.FluxFunction;
import org.springframework.cloud.function.core.FluxSupplier;
import org.springframework.cloud.function.core.FluxToMonoFunction;
import org.springframework.cloud.function.core.IsolatedConsumer;
import org.springframework.cloud.function.core.IsolatedFunction;
import org.springframework.cloud.function.core.IsolatedSupplier;
import org.springframework.cloud.function.core.MonoToFluxFunction;
import org.springframework.cloud.function.json.GsonMapper;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 */
@Configuration
@ConditionalOnMissingBean(FunctionCatalog.class)
// @checkstyle:off
@ComponentScan(basePackages = "${spring.cloud.function.scan.packages:functions}", includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
		Supplier.class, Function.class, Consumer.class }))
// @checkstyle:on
public class ContextFunctionCatalogAutoConfiguration {

	static final String PREFERRED_MAPPER_PROPERTY = "spring.http.converters.preferred-json-mapper";

	@Bean
	public FunctionRegistry functionCatalog(ContextFunctionRegistry processor) {
		return new BeanFactoryFunctionCatalog(processor);
	}

	protected static class BeanFactoryFunctionCatalog extends AbstractFunctionRegistry {

		private final ContextFunctionRegistry processor;

		public BeanFactoryFunctionCatalog(ContextFunctionRegistry processor) {
			this.processor = processor;
		}

		@Override
		public FunctionRegistration<?> getRegistration(Object function) {
			return function == null ? null : this.processor.getRegistration(function);
		}

		@Override
		public <T> void register(FunctionRegistration<T> registration) {
			Assert.notEmpty(registration.getNames(),
					"'registration' must contain at least one name before it is registered in catalog.");
			this.processor.register(registration);
		}

		@Override
		public Set<String> getNames(Class<?> type) {
			if (Supplier.class.isAssignableFrom(type)) {
				return this.processor.suppliers.keySet();
			}
			if (Consumer.class.isAssignableFrom(type)) {
				return this.processor.consumers.keySet();
			}
			if (Function.class.isAssignableFrom(type)) {
				return this.processor.functions.keySet();
			}
			return Collections.emptySet();
		}

		@Override
		public int size() {
			return this.processor.suppliers.size() + this.processor.functions.size()
					+ this.processor.consumers.size();
		}

		@Override
		@SuppressWarnings("unchecked")
		protected <T> T doLookup(Class<?> type, String name) {
			T function = null;
			if (type == null) {
				function = (T) this.processor.lookupFunction(name);
				if (function == null) {
					function = (T) this.processor.lookupConsumer(name);
				}
				if (function == null) {
					function = (T) this.processor.lookupSupplier(name);
				}
			}
			else if (Function.class.isAssignableFrom(type)) {
				function = (T) this.processor.lookupFunction(name);
			}
			else if (Supplier.class.isAssignableFrom(type)) {
				function = (T) this.processor.lookupSupplier(name);
			}
			else if (Consumer.class.isAssignableFrom(type)) {
				function = (T) this.processor.lookupConsumer(name);
			}
			return function;
		}

	}

	@Component
	protected static class ContextFunctionRegistry
			implements SmartInitializingSingleton, BeanFactoryAware {

		private Log logger = LogFactory.getLog(ContextFunctionRegistry.class);

		private ApplicationEventPublisher applicationEventPublisher;

		private ConfigurableListableBeanFactory beanFactory;

		private Map<String, Object> suppliers = new ConcurrentHashMap<>();

		private Map<String, Object> functions = new ConcurrentHashMap<>();

		private Map<String, Object> consumers = new ConcurrentHashMap<>();

		private Map<Object, String> names = new ConcurrentHashMap<>();

		private Map<String, FunctionType> types = new ConcurrentHashMap<>();

		/**
		 * Will collect all suppliers, functions, consumers and function registration as
		 * late as possible in the lifecycle.
		 */
		@Override
		@SuppressWarnings("rawtypes")
		public void afterSingletonsInstantiated() {
			Map<String, Supplier> supplierBeans = beanFactory
					.getBeansOfType(Supplier.class);
			Map<String, Function> functionBeans = beanFactory
					.getBeansOfType(Function.class);
			Map<String, Consumer> consumerBeans = beanFactory
					.getBeansOfType(Consumer.class);
			Map<String, FunctionRegistration> functionRegistrationBeans = beanFactory
					.getBeansOfType(FunctionRegistration.class);
			this.doMerge(functionRegistrationBeans, consumerBeans, supplierBeans,
					functionBeans);
		}

		@Override
		public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
			this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
		}

		public <T> void register(FunctionRegistration<T> function) {
			wrap(function, function.getNames().iterator().next());
		}

		@PreDestroy
		public void close() {
			if (this.applicationEventPublisher != null) {
				if (!this.functions.isEmpty()) {
					this.applicationEventPublisher
							.publishEvent(new FunctionUnregistrationEvent(this,
									Function.class, this.functions.keySet()));
				}
				if (!this.consumers.isEmpty()) {
					this.applicationEventPublisher
							.publishEvent(new FunctionUnregistrationEvent(this,
									Consumer.class, this.consumers.keySet()));
				}
				if (!this.suppliers.isEmpty()) {
					this.applicationEventPublisher
							.publishEvent(new FunctionUnregistrationEvent(this,
									Supplier.class, this.suppliers.keySet()));
				}
			}
		}

		FunctionRegistration<?> getRegistration(Object function) {
			if (names.containsKey(function)) {
				return new FunctionRegistration<>(function, this.names.get(function))
						.type(findType(function).getType());
			}
			return null;
		}

		Supplier<?> lookupSupplier(String name) {
			return (Supplier<?>) lookup(name, this.suppliers, Supplier.class);
		}

		Function<?, ?> lookupFunction(String name) {
			return (Function<?, ?>) lookup(name, this.functions, Function.class);
		}

		Consumer<?> lookupConsumer(String name) {
			return (Consumer<?>) lookup(name, this.consumers, Consumer.class);
		}

		@SuppressWarnings("unchecked")
		private Object lookup(String name, @SuppressWarnings("rawtypes") Map lookup,
				Class<?> typeOfFunction) {
			Object function = compose(name, lookup);
			if (function != null
					&& typeOfFunction.isAssignableFrom(function.getClass())) {
				return function;
			}
			else {
				logger.warn("The resulting composition is is of type "
						+ types.get(normalizeName(name)));
			}
			return null;
		}

		// @checkstyle:off
		/**
		 * @deprecated Was never intended for public use.
		 */
		@Deprecated
		@SuppressWarnings("rawtypes")
		Set<FunctionRegistration<?>> merge(Map<String, FunctionRegistration> initial,
				Map<String, Consumer> consumers, Map<String, Supplier> suppliers,
				Map<String, Function> functions) {
			this.doMerge(initial, consumers, suppliers, functions);
			return null;
		}
		// @checkstyle:on

		private String normalizeName(String name) {
			return name.replaceAll(",", "|").trim();
		}

		private Object compose(String name, Map<String, Object> lookup) {
			name = normalizeName(name);
			Object composedFunction = null;
			if (lookup.containsKey(name)) {
				composedFunction = lookup.get(name);
			}
			else {
				if (name.equals("") && lookup.size() == 1) {
					composedFunction = lookup.values().iterator().next();
				}
				else {
					String[] stages = StringUtils.delimitedListToStringArray(name, "|");
					if (Stream.of(stages).allMatch(funcName -> contains(funcName))) {
						List<Object> composableFunctions = Stream.of(stages)
								.map(funcName -> find(funcName))
								.collect(Collectors.toList());
						composedFunction = composableFunctions.stream()
								.reduce((a, z) -> composeFunctions(a, z))
								.orElseGet(() -> null);
						if (composedFunction != null && !this.types.containsKey(name)
								&& this.types.containsKey(stages[0])
								&& this.types.containsKey(stages[stages.length - 1])) {
							FunctionType input = this.types.get(stages[0]);
							FunctionType output = this.types
									.get(stages[stages.length - 1]);
							this.types.put(name, FunctionType.compose(input, output));
							this.names.put(composedFunction, name);
							lookup.put(name, composedFunction);
						}
					}
				}
			}
			return composedFunction;
		}

		private boolean contains(String name) {
			return suppliers.containsKey(name) || functions.containsKey(name)
					|| consumers.containsKey(name);
		}

		private Object find(String name) {
			Object result = suppliers.get(name);
			if (result == null) {
				result = functions.get(name);
			}
			if (result == null) {
				result = consumers.get(name);
			}
			return result;
		}

		@SuppressWarnings("unchecked")
		private Object composeFunctions(Object a, Object b) {
			if (a instanceof Supplier && b instanceof Function) {
				Supplier<Flux<Object>> supplier = (Supplier<Flux<Object>>) a;
				if (b instanceof FluxConsumer) {
					if (supplier instanceof FluxSupplier) {
						FluxConsumer<Object> fConsumer = ((FluxConsumer<Object>) b);
						return (Supplier<Mono<Void>>) () -> Mono.from(supplier.get()
								.compose(v -> fConsumer.apply(supplier.get())));
					}
					else {
						throw new IllegalStateException(
								"The provided supplier is finite (i.e., already composed with Consumer) "
										+ "therefore it can not be composed with another consumer");
					}
				}
				else {
					Function<Object, Object> function = (Function<Object, Object>) b;
					return (Supplier<Object>) () -> function.apply(supplier.get());
				}
			}
			else if (a instanceof Function && b instanceof Function) {
				Function<Object, Object> function1 = (Function<Object, Object>) a;
				Function<Object, Object> function2 = (Function<Object, Object>) b;
				if (function1 instanceof FluxToMonoFunction) {
					if (function2 instanceof MonoToFluxFunction) {
						return function1.andThen(function2);
					}
					else {
						throw new IllegalStateException(
								"The provided function is finite (i.e., returns Mono<?>) "
										+ "therefore it can *only* be composed with compatible function (i.e., Function<Mono, Flux>");
					}
				}
				else if (function2 instanceof FluxToMonoFunction) {
					return new FluxToMonoFunction<Object, Object>(
							((Function<Flux<Object>, Flux<Object>>) a)
									.andThen(((FluxToMonoFunction<Object, Object>) b)
											.getTarget()));
				}
				else {
					return function1.andThen(function2);
				}
			}
			else if (a instanceof Function && b instanceof Consumer) {
				Function<Object, Object> function = (Function<Object, Object>) a;
				Consumer<Object> consumer = (Consumer<Object>) b;
				return (Consumer<Object>) v -> consumer.accept(function.apply(v));
			}
			else {
				throw new IllegalArgumentException(String.format(
						"Could not compose %s and %s", a.getClass(), b.getClass()));
			}
		}

		private Collection<String> getAliases(String key) {
			Collection<String> names = new LinkedHashSet<>();
			String value = getQualifier(key);
			if (value.equals(key) && this.beanFactory != null) {
				names.addAll(Arrays.asList(this.beanFactory.getAliases(key)));
			}
			names.add(value);
			return names;
		}

		private void wrap(FunctionRegistration<?> registration, String key) {
			Object target = registration.getTarget();
			this.names.put(target, key);
			if (registration.getType() != null) {
				this.types.put(key, registration.getType());
			}
			else {
				registration.type(findType(target).getType());
			}
			Class<?> type;
			registration = transform(registration);
			target = registration.getTarget();
			if (target instanceof Supplier) {
				type = Supplier.class;
				for (String name : registration.getNames()) {
					this.suppliers.put(name, registration.getTarget());
				}
			}
			else if (target instanceof Consumer) {
				type = Consumer.class;
				for (String name : registration.getNames()) {
					this.consumers.put(name, registration.getTarget());
				}
			}
			else if (target instanceof Function) {
				type = Function.class;
				for (String name : registration.getNames()) {
					this.functions.put(name, registration.getTarget());
				}
			}
			else {
				return;
			}
			this.names.put(registration.getTarget(), key);
			if (this.applicationEventPublisher != null) {
				this.applicationEventPublisher.publishEvent(new FunctionRegistrationEvent(
						registration.getTarget(), type, registration.getNames()));
			}
		}

		private FunctionRegistration<?> transform(FunctionRegistration<?> registration) {
			return fluxify(isolated(registration));
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private FunctionRegistration<?> fluxify(FunctionRegistration<?> input) {
			FunctionRegistration<Object> registration = (FunctionRegistration<Object>) input;
			Object target = registration.getTarget();
			FunctionType type = registration.getType();
			boolean flux = hasFluxTypes(type);
			if (!flux) {
				if (target instanceof Supplier<?>) {
					target = new FluxSupplier((Supplier<?>) target);
				}
				else if (target instanceof Function<?, ?>) {
					target = new FluxFunction((Function<?, ?>) target);
				}
				else if (target instanceof Consumer<?>) {
					target = new FluxConsumer((Consumer<?>) target);
				}
				registration.target(target);
			}
			if (Mono.class.isAssignableFrom(type.getOutputWrapper())) {
				registration.target(new FluxToMonoFunction<>((Function) target));
			}
			else if (Mono.class.isAssignableFrom(type.getInputWrapper())) {
				registration.target(new MonoToFluxFunction<>((Function) target));
			}
			return registration;
		}

		private boolean hasFluxTypes(FunctionType type) {
			return type.isWrapper();
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private FunctionRegistration<?> isolated(FunctionRegistration<?> input) {
			FunctionRegistration<Object> registration = (FunctionRegistration<Object>) input;
			Object target = registration.getTarget();
			boolean isolated = getClass().getClassLoader() != target.getClass()
					.getClassLoader();
			if (isolated) {
				if (target instanceof Supplier<?> && isolated) {
					target = new IsolatedSupplier((Supplier<?>) target);
				}
				else if (target instanceof Function<?, ?>) {
					target = new IsolatedFunction((Function<?, ?>) target);
				}
				else if (target instanceof Consumer<?>) {
					target = new IsolatedConsumer((Consumer<?>) target);
				}
			}

			registration.target(target);
			return registration;
		}

		private String getQualifier(String key) {
			if (this.beanFactory != null
					&& this.beanFactory.containsBeanDefinition(key)) {
				BeanDefinition beanDefinition = this.beanFactory.getBeanDefinition(key);
				Object source = beanDefinition.getSource();
				if (source instanceof StandardMethodMetadata) {
					StandardMethodMetadata metadata = (StandardMethodMetadata) source;
					Qualifier qualifier = AnnotatedElementUtils.findMergedAnnotation(
							metadata.getIntrospectedMethod(), Qualifier.class);
					if (qualifier != null && qualifier.value().length() > 0) {
						return qualifier.value();
					}
				}
			}
			return key;
		}

		private FunctionType findType(Object function) {
			String name = this.names.get(function);
			if (this.types.containsKey(name)) {
				return this.types.get(name);
			}
			FunctionType param;
			if (name == null || this.beanFactory == null
					|| !this.beanFactory.containsBeanDefinition(name)) {
				if (function != null) {
					param = new FunctionType(function.getClass());
				}
				else {
					param = FunctionType.UNCLASSIFIED;
				}
			}
			else {
				param = new FunctionType(
						FunctionContextUtils.findType(name, this.beanFactory));
			}
			this.types.computeIfAbsent(name, str -> param);
			return param;
		}

		@SuppressWarnings("rawtypes")
		private void doMerge(Map<String, FunctionRegistration> functionRegistrationBeans,
				Map<String, Consumer> consumerBeans, Map<String, Supplier> supplierBeans,
				Map<String, Function> functionBeans) {

			Set<FunctionRegistration<?>> registrations = new HashSet<>();
			Map<Object, String> targets = new HashMap<>();
			// Replace the initial registrations with new ones that have the right names
			for (String key : functionRegistrationBeans.keySet()) {
				FunctionRegistration<?> registration = functionRegistrationBeans.get(key);
				if (registration.getNames().isEmpty()) {
					registration.names(getAliases(key));
				}
				registrations.add(registration);
				targets.put(registration.getTarget(), key);
			}

			Stream.concat(consumerBeans.entrySet().stream(), Stream.concat(
					supplierBeans.entrySet().stream(), functionBeans.entrySet().stream()))
					.forEach(entry -> {
						if (!targets.containsKey(entry.getValue())) {
							FunctionRegistration<Object> target = new FunctionRegistration<Object>(
									entry.getValue(),
									getAliases(entry.getKey()).toArray(new String[] {}));
							targets.put(target.getTarget(), entry.getKey());
							registrations.add(target);
						}
					});
			// Wrap the functions so they handle reactive inputs and outputs
			registrations.forEach(registration -> wrap(registration,
					targets.get(registration.getTarget())));
		}

	}

	private static class PreferGsonOrMissingJacksonCondition extends AnyNestedCondition {

		PreferGsonOrMissingJacksonCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(name = PREFERRED_MAPPER_PROPERTY, havingValue = "gson", matchIfMissing = false)
		static class GsonPreferred {

		}

		@ConditionalOnMissingBean(ObjectMapper.class)
		static class JacksonMissing {

		}

	}

	protected class BeanFactoryFunctionInspector implements FunctionInspector {

		private ContextFunctionRegistry processor;

		public BeanFactoryFunctionInspector(ContextFunctionRegistry processor) {
			this.processor = processor;
		}

		@Override
		public FunctionRegistration<?> getRegistration(Object function) {
			FunctionRegistration<?> registration = this.processor
					.getRegistration(function);
			return registration;
		}

	}

	@Configuration
	@ConditionalOnClass(Gson.class)
	@ConditionalOnBean(Gson.class)
	@Conditional(PreferGsonOrMissingJacksonCondition.class)
	protected static class GsonConfiguration {

		@Bean
		public GsonMapper jsonMapper(Gson gson) {
			return new GsonMapper(gson);
		}

	}

	@Configuration
	@ConditionalOnClass(ObjectMapper.class)
	@ConditionalOnBean(ObjectMapper.class)
	// @checkstyle:off
	@ConditionalOnProperty(name = ContextFunctionCatalogAutoConfiguration.PREFERRED_MAPPER_PROPERTY, havingValue = "jackson", matchIfMissing = true)
	// @checkstyle:on
	protected static class JacksonConfiguration {

		@Bean
		public JacksonMapper jsonMapper(ObjectMapper mapper) {
			return new JacksonMapper(mapper);
		}

	}

}
