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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
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
import org.springframework.core.type.StandardMethodMetadata;
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

	@Bean
	public FunctionCatalog functionCatalog(ContextFunctionPostProcessor processor,
			ObjectMapper mapper) {
		return new InMemoryFunctionCatalog(processor.wrapSuppliers(mapper, suppliers),
				processor.wrapFunctions(mapper, functions),
				processor.wrapConsumers(mapper, consumers));
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

		public Map<String, Supplier<?>> wrapSuppliers(ObjectMapper mapper,
				Map<String, Supplier<?>> suppliers) {
			Map<String, Supplier<?>> result = new HashMap<>();
			for (String key : suppliers.keySet()) {
				Supplier<?> target = target(suppliers.get(key), mapper, key);
				result.put(key, target);
				for (String name : registry.getAliases(key)) {
					result.put(name, target);
				}
			}
			return result;
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
				return value;
			}
			else {
				return target;
			}
		}

		public Map<String, Function<?, ?>> wrapFunctions(ObjectMapper mapper,
				Map<String, Function<?, ?>> functions) {
			Map<String, Function<?, ?>> result = new HashMap<>();
			for (String key : functions.keySet()) {
				Function<?, ?> target = target(functions.get(key), mapper, key);
				result.put(key, target);
				for (String name : registry.getAliases(key)) {
					result.put(name, target);
				}
			}
			return result;
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
				return value;
			}
			else {
				return target;
			}
		}

		public Map<String, Consumer<?>> wrapConsumers(ObjectMapper mapper,
				Map<String, Consumer<?>> consumers) {
			Map<String, Consumer<?>> result = new HashMap<>();
			for (String key : consumers.keySet()) {
				Consumer<?> target = target(consumers.get(key), mapper, key);
				result.put(key, target);
				for (String name : registry.getAliases(key)) {
					result.put(name, target);
				}
			}
			return result;
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
						if (types != null && types.length == 2) {
							return (types[0].getTypeName()
									.startsWith(Flux.class.getName())
									&& types[1].getTypeName()
											.startsWith(Flux.class.getName()));
						}
					}
				}
			}
			return FunctionUtils.isFluxFunction(function);
		}

		private boolean isFluxConsumer(String name, Consumer<?> function) {
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
						if (types != null && types.length == 1) {
							return (types[0].getTypeName()
									.startsWith(Flux.class.getName()));
						}
					}
				}
			}
			return FunctionUtils.isFluxConsumer(function);
		}

		private boolean isFluxSupplier(String name, Supplier<?> function) {
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
						if (types != null && types.length == 1) {
							return (types[0].getTypeName()
									.startsWith(Flux.class.getName()));
						}
					}
				}
			}
			return FunctionUtils.isFluxSupplier(function);
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

		private Class<?> findType(RootBeanDefinition definition, int index) {
			StandardMethodMetadata source = (StandardMethodMetadata) definition
					.getSource();
			Type param;
			if (source instanceof StandardMethodMetadata) {
				ParameterizedType type;
				type = (ParameterizedType) (source.getIntrospectedMethod()
						.getGenericReturnType());
				type = (ParameterizedType) type.getActualTypeArguments()[index];
				param = type.getActualTypeArguments()[0];
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
			return findType((RootBeanDefinition) registry.getBeanDefinition(name), 0);
		}

		private Class<?> findOutputType(String name) {
			return findType((RootBeanDefinition) registry.getBeanDefinition(name), 1);
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
