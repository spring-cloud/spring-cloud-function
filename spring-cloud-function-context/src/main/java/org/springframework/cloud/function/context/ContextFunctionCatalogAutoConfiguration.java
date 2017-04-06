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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.cloud.function.support.FluxConsumer;
import org.springframework.cloud.function.support.FluxFunction;
import org.springframework.cloud.function.support.FluxSupplier;
import org.springframework.cloud.function.support.FunctionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

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
	public FunctionCatalog functionCatalog(ContextFunctionPostProcessor processor,
			ObjectMapper mapper) {
		return new InMemoryFunctionCatalog(
				processor.merge(registrations, consumers, suppliers, functions, mapper));
	}

	@Component
	public static class ContextFunctionPostProcessor
			implements BeanFactoryPostProcessor, BeanDefinitionRegistryPostProcessor {

		private Set<String> suppliers = new HashSet<>();
		private Set<String> functions = new HashSet<>();
		private Set<String> consumers = new HashSet<>();

		private BeanDefinitionRegistry registry;

		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
			this.registry = registry;
		}

		public Set<FunctionRegistration<?>> merge(
				Map<String, FunctionRegistration<?>> initial,
				Map<String, Consumer<?>> consumers, Map<String, Supplier<?>> suppliers,
				Map<String, Function<?, ?>> functions, ObjectMapper mapper) {
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
				wrap(target, mapper, key);
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

		private void wrap(FunctionRegistration<Object> registration,
				ObjectMapper mapper, String key) {
			Object target = registration.getTarget();
			if (target instanceof Supplier) {
				registration.target(target((Supplier<?>) target, mapper, key));
			}
			else if (target instanceof Consumer) {
				registration.target(target((Consumer<?>) target, mapper, key));
			}
			else if (target instanceof Function) {
				registration.target(target((Function<?, ?>) target, mapper, key));
			}
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

		private Supplier<?> target(Supplier<?> target, ObjectMapper mapper, String key) {
			if (this.suppliers.contains(key)) {
				@SuppressWarnings("unchecked")
				Supplier<Flux<?>> supplier = (Supplier<Flux<?>>) target;
				return wrapSupplier(supplier, mapper, key);
			}
			else if (!isFluxSupplier(key, target)) {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				FluxSupplier value = new FluxSupplier(target);
				return wrapSupplier(value, mapper, key);
			}
			else {
				return target;
			}
		}

		private Function<?, ?> target(Function<?, ?> target, ObjectMapper mapper,
				String key) {
			if (this.functions.contains(key)) {
				@SuppressWarnings("unchecked")
				Function<Flux<?>, Flux<?>> function = (Function<Flux<?>, Flux<?>>) target;
				return wrapFunction(function, mapper, key);
			}
			else if (!isFluxFunction(key, target)) {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				FluxFunction value = new FluxFunction(target);
				return wrapFunction(value, mapper, key);
			}
			else {
				return target;
			}
		}

		private Consumer<?> target(Consumer<?> target, ObjectMapper mapper, String key) {
			if (this.consumers.contains(key)) {
				@SuppressWarnings("unchecked")
				Consumer<Flux<?>> consumer = (Consumer<Flux<?>>) target;
				return wrapConsumer(consumer, mapper, key);
			}
			else if (!isFluxConsumer(key, target)) {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				FluxConsumer value = new FluxConsumer(target);
				return wrapConsumer(value, mapper, key);
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
			Boolean fluxTypes = this.hasFluxTypes(name, 2);
			return (fluxTypes != null) ? fluxTypes
					: FunctionUtils.isFluxFunction(function);
		}

		private boolean isFluxConsumer(String name, Consumer<?> consumer) {
			Boolean fluxTypes = this.hasFluxTypes(name, 1);
			return (fluxTypes != null) ? fluxTypes
					: FunctionUtils.isFluxConsumer(consumer);
		}

		private boolean isFluxSupplier(String name, Supplier<?> supplier) {
			Boolean fluxTypes = this.hasFluxTypes(name, 1);
			return (fluxTypes != null) ? fluxTypes
					: FunctionUtils.isFluxSupplier(supplier);
		}

		private Boolean hasFluxTypes(String name, int numTypes) {
			if (this.registry.containsBeanDefinition(name)) {
				BeanDefinition beanDefinition = this.registry.getBeanDefinition(name);
				Object source = beanDefinition.getSource();
				if (source instanceof StandardMethodMetadata) {
					StandardMethodMetadata metadata = (StandardMethodMetadata) source;
					Type returnType = metadata.getIntrospectedMethod()
							.getGenericReturnType();
					if (returnType instanceof ParameterizedType) {
						Type[] types = ((ParameterizedType) returnType)
								.getActualTypeArguments();
						if (types != null && types.length == numTypes) {
							String fluxClassName = Flux.class.getName();
							for (Type t : types) {
								if (!(t.getTypeName().startsWith(fluxClassName))) {
									return false;
								}
							}
							return true;
						}
					}
				}
			}
			return null;
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

		private ProxySupplier wrapSupplier(Supplier<Flux<?>> supplier,
				ObjectMapper mapper, String name) {
			ProxySupplier wrapped = new ProxySupplier(mapper);
			wrapped.setDelegate(supplier);
			wrapped.setOutputType(findType(name));
			return wrapped;
		}

		private ProxyFunction wrapFunction(Function<Flux<?>, Flux<?>> function,
				ObjectMapper mapper, String name) {
			ProxyFunction wrapped = new ProxyFunction(mapper);
			wrapped.setDelegate(function);
			wrapped.setInputType(findType(name));
			wrapped.setOutputType(findOutputType(name));
			return wrapped;
		}

		private ProxyConsumer wrapConsumer(Consumer<Flux<?>> consumer,
				ObjectMapper mapper, String name) {
			ProxyConsumer wrapped = new ProxyConsumer(mapper);
			wrapped.setDelegate(consumer);
			wrapped.setInputType(findType(name));
			return wrapped;
		}

		private Class<?> findType(AbstractBeanDefinition definition, int index) {
			Object source = definition.getSource();
			Type param;
			if (source instanceof StandardMethodMetadata) {
				ParameterizedType type;
				type = (ParameterizedType) ((StandardMethodMetadata) source).getIntrospectedMethod()
						.getGenericReturnType();
				Type typeArgumentAtIndex = type.getActualTypeArguments()[index];
				if (typeArgumentAtIndex instanceof ParameterizedType) {
					param = ((ParameterizedType) typeArgumentAtIndex).getActualTypeArguments()[0];
				}
				else {
					param = typeArgumentAtIndex;
				}
			}
			else if (source instanceof FileSystemResource) {
				try {
					Type type = ClassUtils.forName(definition.getBeanClassName(), null);
					if (type instanceof ParameterizedType) {
						Type typeArgumentAtIndex = ((ParameterizedType)type).getActualTypeArguments()[index];
						if (typeArgumentAtIndex instanceof ParameterizedType) {
							param = ((ParameterizedType) typeArgumentAtIndex).getActualTypeArguments()[0];
						} else {
							param = typeArgumentAtIndex;
						}
					}
					else {
						param = type;
					}
				} catch (ClassNotFoundException e) {
					throw new IllegalStateException("Cannot instrospect bean: " + definition, e);
				}
			}
			else {
				ResolvableType resolvable = (ResolvableType) getField(definition,
						"targetType");
				param = resolvable.getGeneric(index).getGeneric(0).getType();
			}
			if (param instanceof ParameterizedType) {
				ParameterizedType concrete = (ParameterizedType) param;
				param = concrete.getRawType();
			}
			return ClassUtils.resolveClassName(param.getTypeName(),
					registry.getClass().getClassLoader());
		}

		private Object getField(Object target, String name) {
			Field field = ReflectionUtils.findField(target.getClass(), name);
			ReflectionUtils.makeAccessible(field);
			return ReflectionUtils.getField(field, target);
		}

		private Class<?> findType(String name) {
			return findType((AbstractBeanDefinition) registry.getBeanDefinition(name), 0);
		}

		private Class<?> findOutputType(String name) {
			return findType((AbstractBeanDefinition) registry.getBeanDefinition(name), 1);
		}

	}
}

abstract class ProxyWrapper<T> {

	private ObjectMapper mapper;

	private T delegate;

	private Class<?> inputType;

	private Class<?> outputType;

	public ProxyWrapper(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public void setDelegate(T delegate) {
		this.delegate = delegate;
	}

	public T getDelegate() {
		return delegate;
	}

	public void setInputType(Class<?> inputType) {
		this.inputType = inputType;
	}

	public void setOutputType(Class<?> outputType) {
		this.outputType = outputType;
	}

	public Class<?> getInputType() {
		return this.inputType;
	}

	public Class<?> getOutputType() {
		return outputType;
	}

	public Object fromJson(String value) {
		if (getInputType().equals(String.class)) {
			return value;
		}
		try {
			return mapper.readValue(value, getInputType());
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert from JSON: " + value);
		}
	}

	public String toJson(Object value) {
		if (String.class.equals(getOutputType()) && value instanceof String) {
			return (String) value;
		}
		try {
			return mapper.writeValueAsString(value);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert to JSON: " + value);
		}
	}

	@Override
	public String toString() {
		return "ProxyWrapper [delegate=" + delegate + ", inputType=" + inputType + "]";
	}

}

class ProxySupplier extends ProxyWrapper<Supplier<Flux<?>>>
		implements Supplier<Flux<String>> {

	@Autowired
	public ProxySupplier(ObjectMapper mapper) {
		super(mapper);
	}

	@Override
	public Flux<String> get() {
		return getDelegate().get().map(this::toJson);
	}
}

class ProxyFunction extends ProxyWrapper<Function<Flux<?>, Flux<?>>>
		implements Function<Flux<String>, Flux<String>> {

	@Autowired
	public ProxyFunction(ObjectMapper mapper) {
		super(mapper);
	}

	@Override
	public Flux<String> apply(Flux<String> input) {
		return getDelegate().apply(input.map(this::fromJson)).map(this::toJson);
	}
}

class ProxyConsumer extends ProxyWrapper<Consumer<Flux<?>>>
		implements Consumer<Flux<String>> {

	@Autowired
	public ProxyConsumer(ObjectMapper mapper) {
		super(mapper);
	}

	@Override
	public void accept(Flux<String> input) {
		getDelegate().accept(input.map(this::fromJson));
	}
}
