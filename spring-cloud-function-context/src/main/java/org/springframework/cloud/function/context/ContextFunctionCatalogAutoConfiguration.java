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

package org.springframework.cloud.function.context;

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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.cloud.function.support.FluxConsumer;
import org.springframework.cloud.function.support.FluxFunction;
import org.springframework.cloud.function.support.FluxSupplier;
import org.springframework.cloud.function.support.FunctionFactoryMetadata;
import org.springframework.cloud.function.support.FunctionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.core.type.classreading.MethodMetadataReadingVisitor;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 * @author Mark Fisher
 */
@Configuration
@ConditionalOnClass(InMemoryFunctionCatalog.class)
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
	public FunctionCatalog functionCatalog(ContextFunctionPostProcessor processor) {
		return new InMemoryFunctionCatalog(
				processor.merge(registrations, consumers, suppliers, functions));
	}

	@Bean
	public FunctionInspector functionInspector(ContextFunctionPostProcessor processor) {
		return new BeanFactoryFunctionInspector(processor);
	}

	protected class BeanFactoryFunctionInspector implements FunctionInspector {

		private ContextFunctionPostProcessor processor;

		public BeanFactoryFunctionInspector(ContextFunctionPostProcessor processor) {
			this.processor = processor;
		}

		@Override
		public boolean isMessage(Object function) {
			return processor.isMessage(function);
		}

		@Override
		public Class<?> getInputWrapper(Object function) {
			return processor.findInputWrapper(function);
		}

		@Override
		public Class<?> getOutputWrapper(Object function) {
			return processor.findOutputWrapper(function);
		}

		@Override
		public Class<?> getInputType(Object function) {
			return processor.findInputType(function);
		}

		@Override
		public Class<?> getOutputType(Object function) {
			return processor.findOutputType(function);
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
	protected static class ContextFunctionPostProcessor
			implements BeanDefinitionRegistryPostProcessor {

		private Set<String> suppliers = new HashSet<>();
		private Set<String> functions = new HashSet<>();
		private Set<String> consumers = new HashSet<>();

		private BeanDefinitionRegistry registry;
		private ConversionService conversionService;
		private Map<Object, String> registrations = new HashMap<>();

		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
			this.registry = registry;
		}

		private Object convert(Object function, String value) {
			if (conversionService == null) {
				if (registry instanceof ConfigurableListableBeanFactory) {
					ConversionService conversionService = ((ConfigurableBeanFactory) this.registry)
							.getConversionService();
					if (conversionService != null) {
						this.conversionService = conversionService;
					}
					else {
						this.conversionService = new DefaultConversionService();
					}
				}
			}
			Class<?> type = findInputType(function);
			return conversionService.canConvert(String.class, type)
					? conversionService.convert(value, type)
					: value;
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
					FunctionRegistration<Object> target = new FunctionRegistration<Object>()
							.target(consumers.get(key)).names(getAliases(key));
					targets.put(target.getTarget(), key);
					registrations.add(target);
				}
			}
			// Add suppliers that were not already registered
			for (String key : suppliers.keySet()) {
				if (!targets.containsKey(suppliers.get(key))) {
					FunctionRegistration<Object> target = new FunctionRegistration<Object>()
							.target(suppliers.get(key)).names(getAliases(key));
					targets.put(target.getTarget(), key);
					registrations.add(target);
				}
			}
			// Add functions that were not already registered
			for (String key : functions.keySet()) {
				if (!targets.containsKey(functions.get(key))) {
					FunctionRegistration<Object> target = new FunctionRegistration<Object>()
							.target(functions.get(key)).names(getAliases(key));
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

		private Collection<String> getAliases(String key) {
			Collection<String> names = new LinkedHashSet<>();
			String value = getQualifier(key);
			if (value.equals(key)) {
				names.add(key);
				names.addAll(Arrays.asList(registry.getAliases(key)));
			}
			else {
				names.add(value);
			}
			return names;
		}

		private void wrap(FunctionRegistration<Object> registration, String key) {
			Object target = registration.getTarget();
			this.registrations.put(target, key);
			if (target instanceof Supplier) {
				registration.target(target((Supplier<?>) target, key));
			}
			else if (target instanceof Consumer) {
				registration.target(target((Consumer<?>) target, key));
			}
			else if (target instanceof Function) {
				registration.target(target((Function<?, ?>) target, key));
			}
			registrations.remove(target);
			this.registrations.put(registration.getTarget(), key);
		}

		private String getQualifier(String key) {
			if (!registry.containsBeanDefinition(key)) {
				return key;
			}
			String value = key;
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
			return value;
		}

		private Supplier<?> target(Supplier<?> target, String key) {
			if (this.suppliers.contains(key)) {
				@SuppressWarnings("unchecked")
				Supplier<Flux<?>> supplier = (Supplier<Flux<?>>) target;
				return supplier;
			}
			else if (!isFluxSupplier(key, target)) {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				FluxSupplier value = new FluxSupplier(target);
				return value;
			}
			else {
				return target;
			}
		}

		private Function<?, ?> target(Function<?, ?> target, String key) {
			if (this.functions.contains(key)) {
				@SuppressWarnings("unchecked")
				Function<Flux<?>, Flux<?>> function = (Function<Flux<?>, Flux<?>>) target;
				return function;
			}
			else if (!isFluxFunction(key, target)) {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				FluxFunction value = new FluxFunction(target);
				return value;
			}
			else {
				return target;
			}
		}

		private Consumer<?> target(Consumer<?> target, String key) {
			if (this.consumers.contains(key)) {
				@SuppressWarnings("unchecked")
				Consumer<Flux<?>> consumer = (Consumer<Flux<?>>) target;
				return consumer;
			}
			else if (!isFluxConsumer(key, target)) {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				FluxConsumer value = new FluxConsumer(target);
				return value;
			}
			else {
				return target;
			}
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory factory)
				throws BeansException {
			for (String name : factory.getBeanDefinitionNames()) {
				if (isGenericSupplier(factory, name)) {
					this.suppliers.add(name);
				}
				else if (isGenericFunction(factory, name)) {
					this.functions.add(name);
				}
				else if (isGenericConsumer(factory, name)) {
					this.consumers.add(name);
				}
			}
		}

		private boolean isFluxFunction(String name, Function<?, ?> function) {
			boolean fluxTypes = this.hasFluxTypes(function);
			return fluxTypes || FunctionUtils.isFluxFunction(function);
		}

		private boolean isFluxConsumer(String name, Consumer<?> consumer) {
			boolean fluxTypes = this.hasFluxTypes(consumer);
			return fluxTypes || FunctionUtils.isFluxConsumer(consumer);
		}

		private boolean isFluxSupplier(String name, Supplier<?> supplier) {
			boolean fluxTypes = this.hasFluxTypes(supplier);
			return fluxTypes || FunctionUtils.isFluxSupplier(supplier);
		}

		private boolean hasFluxTypes(Object function) {
			return FunctionInspector.isWrapper(findInputWrapper(function))
					|| FunctionInspector.isWrapper(findOutputWrapper(function));
		}

		private boolean isGenericSupplier(ConfigurableListableBeanFactory factory,
				String name) {
			return factory.isTypeMatch(name,
					ResolvableType.forClassWithGenerics(Supplier.class, Flux.class))
					&& !factory.isTypeMatch(name,
							ResolvableType.forClassWithGenerics(Supplier.class,
									ResolvableType.forClassWithGenerics(Flux.class,
											String.class)));
		}

		private boolean isGenericFunction(ConfigurableListableBeanFactory factory,
				String name) {
			return factory.isTypeMatch(name,
					ResolvableType.forClassWithGenerics(Function.class, Flux.class,
							Flux.class))
					&& !factory.isTypeMatch(name,
							ResolvableType.forClassWithGenerics(Function.class,
									ResolvableType.forClassWithGenerics(Flux.class,
											String.class),
									ResolvableType.forClassWithGenerics(Flux.class,
											String.class)));
		}

		private boolean isGenericConsumer(ConfigurableListableBeanFactory factory,
				String name) {
			return factory.isTypeMatch(name,
					ResolvableType.forClassWithGenerics(Consumer.class, Flux.class))
					&& !factory.isTypeMatch(name,
							ResolvableType.forClassWithGenerics(Consumer.class,
									ResolvableType.forClassWithGenerics(Flux.class,
											String.class)));
		}

		private Class<?> findType(String name, AbstractBeanDefinition definition,
				ParamType paramType) {
			Object source = definition.getSource();
			Type param = null;
			// Start by assuming output -> Function
			int index = paramType.isOutput() ? 1 : 0;
			if (source instanceof StandardMethodMetadata) {
				// Standard @Bean metadata
				ParameterizedType type = (ParameterizedType) ((StandardMethodMetadata) source)
						.getIntrospectedMethod().getGenericReturnType();
				param = extractType(type, paramType, index);
			}
			else if (source instanceof MethodMetadataReadingVisitor) {
				// A component scan with @Beans
				MethodMetadataReadingVisitor visitor = (MethodMetadataReadingVisitor) source;
				Type type = findBeanType(definition, visitor);
				param = extractType(type, paramType, index);
			}
			else if (source instanceof Resource) {
				try {
					Class<?> beanType = ClassUtils.forName(definition.getBeanClassName(),
							null);
					for (Type type : beanType.getGenericInterfaces()) {
						if (type.getTypeName().startsWith("java.util.function")) {
							param = extractType(type, paramType, index);
							break;
						}
					}
					if (param == null) {
						// Last chance
						param = beanType;
					}
				}
				catch (ClassNotFoundException e) {
					throw new IllegalStateException(
							"Cannot instrospect bean: " + definition, e);
				}
			}
			else {
				ResolvableType resolvable = (ResolvableType) getField(definition,
						"targetType");
				if (resolvable != null) {
					param = resolvable.getGeneric(index).getGeneric(0).getType();
				}
				else if (registry instanceof BeanFactory) {
					Object bean = ((BeanFactory) registry).getBean(name);
					if (bean instanceof FunctionFactoryMetadata) {
						FunctionFactoryMetadata factory = (FunctionFactoryMetadata) bean;
						Type type = factory.getFactoryMethod().getGenericReturnType();
						param = extractType(type, paramType, index);
					}
				}
			}
			if (param instanceof ParameterizedType) {
				ParameterizedType concrete = (ParameterizedType) param;
				param = concrete.getRawType();
			}
			if (param == null) {
				// Last ditch attempt to guess: Flux<String>
				if (paramType.isWrapper()) {
					return Flux.class;
				}
				return String.class;
			}
			return (Class<?>) param;
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

		private Type extractType(Type type, ParamType paramType, int index) {
			Type param;
			if (type instanceof ParameterizedType) {
				ParameterizedType parameterizedType = (ParameterizedType) type;
				if (parameterizedType.getActualTypeArguments().length == 1) {
					// There's only one
					index = 0;
				}
				Type typeArgumentAtIndex = parameterizedType
						.getActualTypeArguments()[index];
				if (typeArgumentAtIndex instanceof ParameterizedType
						&& !paramType.isWrapper()) {
					if (FunctionInspector.isWrapper(
							((ParameterizedType) typeArgumentAtIndex).getRawType())) {
						param = ((ParameterizedType) typeArgumentAtIndex)
								.getActualTypeArguments()[0];
						param = extractNestedType(paramType, param);
					}
					else {
						param = extractNestedType(paramType, typeArgumentAtIndex);
					}
				}
				else {
					param = extractNestedType(paramType, typeArgumentAtIndex);
				}
			}
			else {
				param = type;
			}
			return param;
		}

		private Type extractNestedType(ParamType paramType, Type param) {
			if (!paramType.isInnerWrapper()
					&& param.getTypeName().startsWith(Message.class.getName())) {
				if (param instanceof ParameterizedType) {
					param = ((ParameterizedType) param).getActualTypeArguments()[0];
				}
			}
			return param;
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
			String name = registrations.get(function);
			if (name == null || !registry.containsBeanDefinition(name)) {
				return false;
			}
			return Message.class.isAssignableFrom(findType(name,
					(AbstractBeanDefinition) registry.getBeanDefinition(name),
					ParamType.INPUT_INNER_WRAPPER))
					|| Message.class.isAssignableFrom(findType(name,
							(AbstractBeanDefinition) registry.getBeanDefinition(name),
							ParamType.OUTPUT_INNER_WRAPPER));
		}

		private Class<?> findInputWrapper(Object function) {
			String name = registrations.get(function);
			if (name == null || !registry.containsBeanDefinition(name)) {
				return Object.class;
			}
			return findType(name,
					(AbstractBeanDefinition) registry.getBeanDefinition(name),
					ParamType.INPUT_WRAPPER);
		}

		private Class<?> findOutputWrapper(Object function) {
			String name = registrations.get(function);
			if (name == null || !registry.containsBeanDefinition(name)) {
				return Object.class;
			}
			return findType(name,
					(AbstractBeanDefinition) registry.getBeanDefinition(name),
					ParamType.OUTPUT_WRAPPER);
		}

		private Class<?> findInputType(Object function) {
			String name = registrations.get(function);
			if (name == null || !registry.containsBeanDefinition(name)) {
				return Object.class;
			}
			return findType(name,
					(AbstractBeanDefinition) registry.getBeanDefinition(name),
					ParamType.INPUT);
		}

		private Class<?> findOutputType(Object function) {
			String name = registrations.get(function);
			if (name == null || !registry.containsBeanDefinition(name)) {
				return Object.class;
			}
			return findType(name,
					(AbstractBeanDefinition) registry.getBeanDefinition(name),
					ParamType.OUTPUT);
		}

		static enum ParamType {
			INPUT, OUTPUT, INPUT_WRAPPER, OUTPUT_WRAPPER, INPUT_INNER_WRAPPER, OUTPUT_INNER_WRAPPER;

			public boolean isOutput() {
				return this == OUTPUT || this == OUTPUT_WRAPPER
						|| this == OUTPUT_INNER_WRAPPER;
			}

			public boolean isInput() {
				return this == INPUT || this == INPUT_WRAPPER
						|| this == INPUT_INNER_WRAPPER;
			}

			public boolean isWrapper() {
				return this == OUTPUT_WRAPPER || this == INPUT_WRAPPER;
			}

			public boolean isInnerWrapper() {
				return this == OUTPUT_INNER_WRAPPER || this == INPUT_INNER_WRAPPER;
			}
		}
	}
}
