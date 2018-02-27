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

package org.springframework.cloud.function.context.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
import org.springframework.cloud.function.core.FunctionFactoryMetadata;
import org.springframework.cloud.function.core.FunctionFactoryUtils;
import org.springframework.cloud.function.core.IsolatedConsumer;
import org.springframework.cloud.function.core.IsolatedFunction;
import org.springframework.cloud.function.core.IsolatedSupplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.core.type.classreading.MethodMetadataReadingVisitor;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

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
			processor.register(registration);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> Supplier<T> lookupSupplier(String name) {
			Supplier<T> result = (Supplier<T>) processor.lookupSupplier(name);
			return result;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T, R> Function<T, R> lookupFunction(String name) {
			Function<T, R> result = (Function<T, R>) processor.lookupFunction(name);
			return result;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> Consumer<T> lookupConsumer(String name) {
			Consumer<T> result = (Consumer<T>) processor.lookupConsumer(name);
			return result;
		}

		@Override
		public Set<String> getSupplierNames() {
			return this.processor.getSuppliers();
		}

		@Override
		public Set<String> getFunctionNames() {
			return this.processor.getFunctions();
		}

		@Override
		public Set<String> getConsumerNames() {
			return this.processor.getConsumers();
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
		public boolean isMessage(Object function) {
			return processor.isMessage(function);
		}

		@Override
		public Class<?> getInputWrapper(Object function) {
			return processor.findType(function).getInputWrapper();
		}

		@Override
		public Class<?> getOutputWrapper(Object function) {
			return processor.findType(function).getOutputWrapper();
		}

		@Override
		public Class<?> getInputType(Object function) {
			return processor.findType(function).getInputType();
		}

		@Override
		public Class<?> getOutputType(Object function) {
			return processor.findType(function).getOutputType();
		}

		@Override
		public Object convert(Object function, String value) {
			return processor.convert(function, value);
		}

		@Override
		public String getName(Object function) {
			return processor.registrations.get(function);
		}

	}

	@Component
	protected static class ContextFunctionRegistry {

		private Map<String, Object> suppliers = new HashMap<>();

		private Map<String, Object> functions = new HashMap<>();

		private Map<String, Object> consumers = new HashMap<>();

		@Autowired(required = false)
		private ApplicationEventPublisher publisher;

		@Autowired
		private ConfigurableListableBeanFactory registry;

		private ConversionService conversionService;

		private Map<Object, String> registrations = new HashMap<>();

		private Map<String, FunctionType> types = new HashMap<>();

		public Set<String> getSuppliers() {
			return this.suppliers.keySet();
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
			if (lookup.containsKey(name)) {
				return lookup.get(name);
			}
			String[] stages = StringUtils.tokenizeToStringArray(name, ",");
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
			registrations.put(function, name);
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
				Supplier<Object> supplier = (Supplier<Object>) a;
				Function<Object, Object> function = (Function<Object, Object>) b;
				return (Supplier<Object>) () -> function.apply(supplier.get());
			}
			else if (a instanceof Function && b instanceof Function) {
				Function<Object, Object> function1 = (Function<Object, Object>) a;
				Function<Object, Object> function2 = (Function<Object, Object>) b;
				return function1.andThen(function2);
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

		@SuppressWarnings("unchecked")
		public <T> void register(FunctionRegistration<T> function) {
			wrap((FunctionRegistration<Object>) function,
					function.getNames().iterator().next());
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
			// Add consumers that were not already registered
			for (String key : consumers.keySet()) {
				if (!targets.containsKey(consumers.get(key))) {
					FunctionRegistration<Object> target = new FunctionRegistration<Object>(
							consumers.get(key)).names(getAliases(key));
					targets.put(target.getTarget(), key);
					registrations.add(target);
				}
			}
			// Add suppliers that were not already registered
			for (String key : suppliers.keySet()) {
				if (!targets.containsKey(suppliers.get(key))) {
					FunctionRegistration<Object> target = new FunctionRegistration<Object>(
							suppliers.get(key)).names(getAliases(key));
					targets.put(target.getTarget(), key);
					registrations.add(target);
				}
			}
			// Add functions that were not already registered
			for (String key : functions.keySet()) {
				if (!targets.containsKey(functions.get(key))) {
					FunctionRegistration<Object> target = new FunctionRegistration<Object>(
							functions.get(key)).names(getAliases(key));
					targets.put(target.getTarget(), key);
					registrations.add(target);
				}
			}
			// Wrap the functions so they handle reactive inputs and outputs
			for (FunctionRegistration<?> registration : registrations) {
				@SuppressWarnings("unchecked")
				FunctionRegistration<Object> target = (FunctionRegistration<Object>) registration;
				String key = targets.get(target.getTarget());
				wrap(target, key);
			}
			return registrations;
		}

		private Object convert(Object function, String value) {
			if (conversionService == null && registry != null) {
				ConversionService conversionService = this.registry
						.getConversionService();
				this.conversionService = conversionService != null ? conversionService
						: new DefaultConversionService();
			}
			Class<?> type = findType(function).getInputType();
			return conversionService.canConvert(String.class, type)
					? conversionService.convert(value, type)
					: value;
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

		private void wrap(FunctionRegistration<Object> registration, String key) {
			Object target = registration.getTarget();
			this.registrations.put(target, key);
			if (registration.getType() != null) {
				this.types.put(key, registration.getType());
			}
			else {
				findType(target);
			}
			Class<?> type;
			if (target instanceof Supplier) {
				type = Supplier.class;
				registration.target(target((Supplier<?>) target, key));
				for (String name : registration.getNames()) {
					this.suppliers.put(name, registration.getTarget());
				}
			}
			else if (target instanceof Consumer) {
				type = Consumer.class;
				registration.target(target((Consumer<?>) target, key));
				for (String name : registration.getNames()) {
					this.consumers.put(name, registration.getTarget());
				}
			}
			else if (target instanceof Function) {
				type = Function.class;
				registration.target(target((Function<?, ?>) target, key));
				for (String name : registration.getNames()) {
					this.functions.put(name, registration.getTarget());
				}
			}
			else {
				return;
			}
			this.registrations.remove(target);
			this.registrations.put(registration.getTarget(), key);
			if (publisher != null) {
				publisher.publishEvent(new FunctionRegistrationEvent(
						registration.getTarget(), type, registration.getNames()));
			}
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

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private <T> T target(T target, String key) {
			boolean isolated = getClass().getClassLoader() != target.getClass()
					.getClassLoader();
			if (target instanceof Supplier<?>) {
				boolean flux = isFluxSupplier(key, (Supplier<?>) target);
				if (isolated) {
					target = (T) new IsolatedSupplier((Supplier<?>) target);
				}
				if (!flux) {
					target = (T) new FluxSupplier((Supplier<?>) target);
				}
			}
			else if (target instanceof Function<?, ?>) {
				boolean flux = isFluxFunction(key, (Function<?, ?>) target);
				if (isolated) {
					target = (T) new IsolatedFunction((Function<?, ?>) target);
				}
				if (!flux) {
					target = (T) new FluxFunction((Function<?, ?>) target);
				}
			}
			else if (target instanceof Consumer<?>) {
				boolean flux = isFluxConsumer(key, (Consumer<?>) target);
				if (isolated) {
					target = (T) new IsolatedConsumer((Consumer<?>) target);
				}
				if (!flux) {
					target = (T) new FluxConsumer((Consumer<?>) target);
				}
			}
			return target;
		}

		private boolean isFluxFunction(String name, Function<?, ?> function) {
			boolean fluxTypes = this.hasFluxTypes(function);
			return fluxTypes || FunctionFactoryUtils.isFluxFunction(function);
		}

		private boolean isFluxConsumer(String name, Consumer<?> consumer) {
			boolean fluxTypes = this.hasFluxTypes(consumer);
			return fluxTypes || FunctionFactoryUtils.isFluxConsumer(consumer);
		}

		private boolean isFluxSupplier(String name, Supplier<?> supplier) {
			boolean fluxTypes = this.hasFluxTypes(supplier);
			return fluxTypes || FunctionFactoryUtils.isFluxSupplier(supplier);
		}

		private boolean hasFluxTypes(Object function) {
			return findType(function).isWrapper();
		}

		private FunctionType findType(String name, AbstractBeanDefinition definition) {
			Object source = definition.getSource();
			FunctionType param = null;
			// Start by assuming output -> Function
			if (source instanceof StandardMethodMetadata) {
				// Standard @Bean metadata
				Type beanType = ((StandardMethodMetadata) source).getIntrospectedMethod()
						.getGenericReturnType();
				if (beanType instanceof ParameterizedType) {
					ParameterizedType type = (ParameterizedType) beanType;
					param = new FunctionType(type);
				}
				else {
					param = new FunctionType(beanType);
				}
			}
			else if (source instanceof MethodMetadataReadingVisitor) {
				// A component scan with @Beans
				MethodMetadataReadingVisitor visitor = (MethodMetadataReadingVisitor) source;
				Type type = findBeanType(definition, visitor);
				param = new FunctionType(type);
			}
			else if (source instanceof Resource) {
				Class<?> beanType = this.registry.getType(name);
				param = new FunctionType(beanType);
			}
			else {
				ResolvableType resolvable = (ResolvableType) getField(definition,
						"targetType");
				if (resolvable != null) {
					param = new FunctionType(resolvable.getType());
				}
				else {
					Object bean = this.registry.getBean(name);
					if (bean instanceof FunctionFactoryMetadata) {
						FunctionFactoryMetadata<?> factory = (FunctionFactoryMetadata<?>) bean;
						Type type = factory.getFactoryMethod().getGenericReturnType();
						param = new FunctionType(type);
					}
					else {
						param = new FunctionType(bean.getClass());
					}
				}
			}
			return param;
		}

		private Type findBeanType(AbstractBeanDefinition definition,
				MethodMetadataReadingVisitor visitor) {
			Class<?> factory = ClassUtils
					.resolveClassName(visitor.getDeclaringClassName(), null);
			List<Class<?>> params = new ArrayList<>();
			for (ValueHolder holder : definition.getConstructorArgumentValues()
					.getIndexedArgumentValues().values()) {
				params.add(ClassUtils.resolveClassName(holder.getType(), null));
			}
			Method method = ReflectionUtils.findMethod(factory, visitor.getMethodName(),
					params.toArray(new Class<?>[0]));
			Type type = method.getGenericReturnType();
			return type;
		}

		private Object getField(Object target, String name) {
			Field field = ReflectionUtils.findField(target.getClass(), name);
			if (field == null) {
				return null;
			}
			ReflectionUtils.makeAccessible(field);
			return ReflectionUtils.getField(field, target);
		}

		private boolean isMessage(Object function) {
			return findType(function).isMessage();
		}

		private FunctionType findType(Object function) {
			String name = registrations.get(function);
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
				param = findType(name,
						(AbstractBeanDefinition) registry.getBeanDefinition(name));
			}
			types.computeIfAbsent(name, str -> param);
			return param;
		}

	}

}
