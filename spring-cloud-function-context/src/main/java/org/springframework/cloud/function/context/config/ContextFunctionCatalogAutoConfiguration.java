/*
 * Copyright 2016-2018 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionScan;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 */
@FunctionScan
@Configuration
@ConditionalOnMissingBean(FunctionCatalog.class)
public class ContextFunctionCatalogAutoConfiguration {

	static final String PREFERRED_MAPPER_PROPERTY = "spring.http.converters.preferred-json-mapper";

	@Autowired(required = false)
	private Map<String, Supplier<?>> suppliers = Collections.emptyMap();

	@Autowired(required = false)
	private Map<String, Function<?, ?>> functions = Collections.emptyMap();

	@Autowired(required = false)
	private Map<String, Consumer<?>> consumers = Collections.emptyMap();

	@Autowired(required = false)
	private Map<String, FunctionRegistration<?>> registrations = Collections.emptyMap();

	@Bean
	public FunctionRegistry functionCatalog(ContextFunctionRegistry processor) {
		processor.merge(registrations, consumers, suppliers, functions);
		return new BeanFactoryFunctionCatalog(processor);
	}

	@Bean
	public FunctionInspector functionInspector(ContextFunctionRegistry processor) {
		return new BeanFactoryFunctionInspector(processor);
	}

	protected static class BeanFactoryFunctionCatalog implements FunctionRegistry {

		private final ContextFunctionRegistry processor;

		@Override
		public <T> void register(FunctionRegistration<T> registration) {
			Assert.notEmpty(registration.getNames(),
					"'registration' must contain at least one name before it is registered in catalog.");
			processor.register(registration);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T lookup(Class<?> type, String name) {
			T function = null;
			if (type == null) {
				function = (T) processor.lookupFunction(name);
				if (function == null) {
					function = (T) processor.lookupConsumer(name);
				}
				if (function == null) {
					function = (T) processor.lookupSupplier(name);
				}
			}
			else if (Supplier.class.isAssignableFrom(type)) {
				function = (T) processor.lookupSupplier(name);
			}
			else if (Consumer.class.isAssignableFrom(type)) {
				function = (T) processor.lookupConsumer(name);
			}
			else if (Function.class.isAssignableFrom(type)) {
				function = (T) processor.lookupFunction(name);
			}
			return function;
		}

		@Override
		public Set<String> getNames(Class<?> type) {
			if (Supplier.class.isAssignableFrom(type)) {
				return this.processor.getSuppliers();
			}
			if (Consumer.class.isAssignableFrom(type)) {
				return this.processor.getConsumers();
			}
			if (Function.class.isAssignableFrom(type)) {
				return this.processor.getFunctions();
			}
			return Collections.emptySet();
		}

		@Override
		public int size() {
			return this.processor.getSuppliers().size()
					+ this.processor.getFunctions().size()
					+ this.processor.getConsumers().size();
		}

		public BeanFactoryFunctionCatalog(ContextFunctionRegistry processor) {
			this.processor = processor;
		}

	}

	protected class BeanFactoryFunctionInspector implements FunctionInspector {

		private ContextFunctionRegistry processor;

		public BeanFactoryFunctionInspector(ContextFunctionRegistry processor) {
			this.processor = processor;
		}

		@Override
		public FunctionRegistration<?> getRegistration(Object function) {
			FunctionRegistration<?> registration = processor.getRegistration(function);
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
	@ConditionalOnProperty(name = ContextFunctionCatalogAutoConfiguration.PREFERRED_MAPPER_PROPERTY, havingValue = "jackson", matchIfMissing = true)
	protected static class JacksonConfiguration {
		@Bean
		public JacksonMapper jsonMapper(ObjectMapper mapper) {
			return new JacksonMapper(mapper);
		}
	}

	@Component
	protected static class ContextFunctionRegistry {

		private Map<String, Object> suppliers = new ConcurrentHashMap<>();

		private Map<String, Object> functions = new ConcurrentHashMap<>();

		private Map<String, Object> consumers = new ConcurrentHashMap<>();

		@Autowired(required = false)
		private ApplicationEventPublisher publisher;

		@Autowired
		private ConfigurableListableBeanFactory registry;

		private Map<Object, String> names = new ConcurrentHashMap<>();

		private Map<String, FunctionType> types = new ConcurrentHashMap<>();

		public Set<String> getSuppliers() {
			return this.suppliers.keySet();
		}

		public FunctionRegistration<?> getRegistration(Object function) {
			if (function == null || !names.containsKey(function)) {
				return null;
			}
			return new FunctionRegistration<>(function, names.get(function))
					.type(findType(function).getType());
		}

		public Set<String> getConsumers() {
			return this.consumers.keySet();
		}

		public Set<String> getFunctions() {
			return this.functions.keySet();
		}

		public Supplier<?> lookupSupplier(String name) {
			Object composed = compose(name, this.suppliers, false);
			if (composed instanceof Supplier) {
				return (Supplier<?>) composed;
			}
			return null;
		}

		public Consumer<?> lookupConsumer(String name) {
			Object composed = compose(name, this.consumers, true);
			if (composed instanceof Consumer) {
				return (Consumer<?>) composed;
			}
			return null;
		}

		public Function<?, ?> lookupFunction(String name) {
			Object composed = compose(name, this.functions, true);
			if (composed instanceof Function) {
				return (Function<?, ?>) composed;
			}
			return null;
		}

		private Object compose(String name, Map<String, Object> lookup,
				boolean hasInput) {
			name = name.replaceAll(",", "|");
			if (lookup.containsKey(name)) {
				return lookup.get(name);
			}
			String[] stages = StringUtils.delimitedListToStringArray(name, "|");
			Map<String, Object> source = !hasInput || stages.length <= 1 ? lookup
					: this.functions;
			if (stages.length == 0 && source.size() == 1) {
				stages = new String[] { source.keySet().iterator().next() };
			}
			Object function = stages.length > 0 ? lookup(stages[0], source) : null;
			if (function == null) {
				return null;
			}
			Object other = null;
			for (int i = 1; i < stages.length - 1; i++) {
				other = lookup(stages[i], this.functions);
				if (other == null) {
					return null;
				}
				function = compose(function, other);
			}
			if (stages.length > 1) {
				other = lookup(stages[stages.length - 1],
						hasInput ? lookup : this.functions);
				if (other == null) {
					return null;
				}
				function = compose(function, other);
			}
			final Object value = function;
			lookup.computeIfAbsent(name, key -> value);
			if (!types.containsKey(name)) {
				if (types.containsKey(stages[0])
						&& types.containsKey(stages[stages.length - 1])) {
					FunctionType input = types.get(stages[0]);
					FunctionType output = types.get(stages[stages.length - 1]);
					types.put(name, FunctionType.compose(input, output));
				}
			}
			names.put(function, name);
			return function;
		}

		private Object lookup(String name, Map<String, Object> lookup) {
			Object result = lookup.get(name);
			if (result != null) {
				findType(result);
			}
			return result;
		}

		@SuppressWarnings("unchecked")
		private Object compose(Object a, Object b) {
			if (a instanceof Supplier && b instanceof Function) {
				Supplier<Flux<Object>> supplier = (Supplier<Flux<Object>>) a;
				if (b instanceof FluxConsumer) {
					if (supplier instanceof FluxSupplier) {
						FluxConsumer<Object> fConsumer = ((FluxConsumer<Object>)b);
						return (Supplier<Mono<Void>>) () -> Mono.from(supplier.get().compose(v -> fConsumer.apply(supplier.get())));
					}
					else {
						throw new IllegalStateException("The provided supplier is finite (i.e., already composed with Consumer) "
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
						throw new IllegalStateException("The provided function is finite (i.e., returns Mono<?>) "
								+ "therefore it can *only* be composed with compatible function (i.e., Function<Mono, Flux>");
					}
				}
				else if (function2 instanceof FluxToMonoFunction) {
					return new FluxToMonoFunction<Object, Object>(((Function<Flux<Object>, Flux<Object>>)a)
							.andThen(((FluxToMonoFunction<Object,Object>) b).getTarget()));
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

		public <T> void register(FunctionRegistration<T> function) {
			wrap(function, function.getNames().iterator().next());
		}

		@PreDestroy
		public void close() {
			if (publisher != null) {
				if (!functions.isEmpty()) {
					publisher.publishEvent(new FunctionUnregistrationEvent(this,
							Function.class, functions.keySet()));
				}
				if (!consumers.isEmpty()) {
					publisher.publishEvent(new FunctionUnregistrationEvent(this,
							Consumer.class, consumers.keySet()));
				}
				if (!suppliers.isEmpty()) {
					publisher.publishEvent(new FunctionUnregistrationEvent(this,
							Supplier.class, suppliers.keySet()));
				}
			}
		}

		public Set<FunctionRegistration<?>> merge(
				Map<String, FunctionRegistration<?>> initial,
				Map<String, Consumer<?>> consumers, Map<String, Supplier<?>> suppliers,
				Map<String, Function<?, ?>> functions) {
			Set<FunctionRegistration<?>> registrations = new HashSet<>();
			Map<Object, String> targets = new HashMap<>();
			// Replace the initial registrations with new ones that have the right names
			for (String key : initial.keySet()) {
				FunctionRegistration<?> registration = initial.get(key);
				if (registration.getNames().isEmpty()) {
					registration.names(getAliases(key));
				}
				registrations.add(registration);
				targets.put(registration.getTarget(), key);
			}

			Stream.concat(consumers.entrySet().stream(), Stream
					.concat(suppliers.entrySet().stream(), functions.entrySet().stream()))
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
			return registrations;
		}

		private Collection<String> getAliases(String key) {
			Collection<String> names = new LinkedHashSet<>();
			String value = getQualifier(key);
			if (value.equals(key) && registry != null) {
				names.addAll(Arrays.asList(registry.getAliases(key)));
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
			// this.names.remove(target);
			this.names.put(registration.getTarget(), key);
			if (publisher != null) {
				publisher.publishEvent(new FunctionRegistrationEvent(
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
			if (target instanceof Supplier<?>) {
				if (isolated) {
					target = new IsolatedSupplier((Supplier<?>) target);
				}
			}
			else if (target instanceof Function<?, ?>) {
				if (isolated) {
					target = new IsolatedFunction((Function<?, ?>) target);
				}
			}
			else if (target instanceof Consumer<?>) {
				if (isolated) {
					target = new IsolatedConsumer((Consumer<?>) target);
				}
			}
			registration.target(target);
			return registration;
		}

		private String getQualifier(String key) {
			if (registry != null && registry.containsBeanDefinition(key)) {
				BeanDefinition beanDefinition = registry.getBeanDefinition(key);
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
			String name = names.get(function);
			if (types.containsKey(name)) {
				return types.get(name);
			}
			FunctionType param;
			if (name == null || registry == null
					|| !registry.containsBeanDefinition(name)) {
				if (function != null) {
					param = new FunctionType(function.getClass());
				}
				else {
					param = FunctionType.UNCLASSIFIED;
				}
			}
			else {
				param = new FunctionType(FunctionContextUtils.findType(name, registry));
			}
			types.computeIfAbsent(name, str -> param);
			return param;
		}

	}

	private static class PreferGsonOrMissingJacksonCondition extends AnyNestedCondition {

		PreferGsonOrMissingJacksonCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(name = ContextFunctionCatalogAutoConfiguration.PREFERRED_MAPPER_PROPERTY, havingValue = "gson", matchIfMissing = false)
		static class GsonPreferred {

		}

		@ConditionalOnMissingBean(ObjectMapper.class)
		static class JacksonMissing {

		}

	}

}
