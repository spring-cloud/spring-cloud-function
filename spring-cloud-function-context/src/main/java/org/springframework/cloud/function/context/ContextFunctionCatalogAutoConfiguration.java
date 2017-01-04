/*
 * Copyright 2016 the original author or authors.
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
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.function.registry.DefaultFunctionRegistryAutoConfiguration;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import reactor.core.publisher.Flux;

@Configuration
@ConditionalOnClass(ApplicationContextFunctionCatalog.class)
@ConditionalOnMissingBean(FunctionCatalog.class)
@AutoConfigureBefore(DefaultFunctionRegistryAutoConfiguration.class)
public class ContextFunctionCatalogAutoConfiguration {

	@Autowired(required = false)
	private Map<String, Function<?, ?>> functions = Collections.emptyMap();
	@Autowired(required = false)
	private Map<String, Consumer<?>> consumers = Collections.emptyMap();
	@Autowired(required = false)
	private Map<String, Supplier<?>> suppliers = Collections.emptyMap();

	@Bean
	public FunctionCatalog functionCatalog(ContextFunctionPostProcessor processor,
			ObjectMapper mapper) {
		return new ApplicationContextFunctionCatalog(
				processor.wrapFunctions(mapper, functions),
				processor.wrapConsumers(mapper, consumers),
				processor.wrapSuppliers(mapper, suppliers));
	}

	@Component
	public static class ContextFunctionPostProcessor
			implements BeanFactoryPostProcessor, BeanDefinitionRegistryPostProcessor {

		private Set<String> functions = new HashSet<>();
		private Set<String> consumers = new HashSet<>();
		private Set<String> suppliers = new HashSet<>();

		private BeanDefinitionRegistry registry;

		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
				throws BeansException {
			this.registry = registry;
		}

		public Map<String, Supplier<?>> wrapSuppliers(ObjectMapper mapper,
				Map<String, Supplier<?>> suppliers) {
			Map<String, Supplier<?>> result = new HashMap<>();
			for (String key : suppliers.keySet()) {
				if (this.suppliers.contains(key)) {
					@SuppressWarnings("unchecked")
					Supplier<Flux<?>> supplier = (Supplier<Flux<?>>) suppliers.get(key);
					result.put(key, wrapSupplier(supplier, mapper));
				}
				else {
					result.put(key, suppliers.get(key));
				}
			}
			return result;
		}

		public Map<String, Consumer<?>> wrapConsumers(ObjectMapper mapper,
				Map<String, Consumer<?>> consumers) {
			Map<String, Consumer<?>> result = new HashMap<>();
			for (String key : consumers.keySet()) {
				if (this.consumers.contains(key)) {
					@SuppressWarnings("unchecked")
					Consumer<Flux<?>> consumer = (Consumer<Flux<?>>) consumers.get(key);
					result.put(key, wrapConsumer(consumer, mapper, key));
				}
				else {
					result.put(key, consumers.get(key));
				}
			}
			return result;
		}

		public Map<String, Function<?, ?>> wrapFunctions(ObjectMapper mapper,
				Map<String, Function<?, ?>> functions) {
			Map<String, Function<?, ?>> result = new HashMap<>();
			for (String key : functions.keySet()) {
				if (this.functions.contains(key)) {
					@SuppressWarnings("unchecked")
					Function<Flux<?>, Flux<?>> function = (Function<Flux<?>, Flux<?>>) functions
							.get(key);
					result.put(key, wrapFunction(function, mapper, key));
				}
				else {
					result.put(key, functions.get(key));
				}
			}
			return result;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory factory)
				throws BeansException {
			for (String name : factory.getBeanDefinitionNames()) {
				if (isGenericFunction(factory, name)) {
					this.functions.add(name);
				}
				else if (isGenericSupplier(factory, name)) {
					this.suppliers.add(name);
				}
				else if (isGenericConsumer(factory, name)) {
					this.consumers.add(name);
				}
			}
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

		private boolean isGenericSupplier(ConfigurableListableBeanFactory factory,
				String name) {
			return factory.isTypeMatch(name,
					ResolvableType.forClassWithGenerics(Supplier.class, Flux.class))
					&& !factory.isTypeMatch(name,
							ResolvableType.forClassWithGenerics(Supplier.class,
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

		private ProxyFunction wrapFunction(Function<Flux<?>, Flux<?>> function,
				ObjectMapper mapper, String name) {
			ProxyFunction wrapped = new ProxyFunction(mapper);
			wrapped.setDelegate(function);
			wrapped.setType(findType(name));
			return wrapped;
		}

		private ProxySupplier wrapSupplier(Supplier<Flux<?>> supplier,
				ObjectMapper mapper) {
			ProxySupplier wrapped = new ProxySupplier(mapper);
			wrapped.setDelegate(supplier);
			return wrapped;
		}

		private ProxyConsumer wrapConsumer(Consumer<Flux<?>> consumer,
				ObjectMapper mapper, String name) {
			ProxyConsumer wrapped = new ProxyConsumer(mapper);
			wrapped.setDelegate(consumer);
			wrapped.setType(findType(name));
			return wrapped;
		}

		private Class<?> findType(RootBeanDefinition definition) {
			StandardMethodMetadata source = (StandardMethodMetadata) definition
					.getSource();
			ParameterizedType type = (ParameterizedType) (source.getIntrospectedMethod()
					.getGenericReturnType());
			type = (ParameterizedType) type.getActualTypeArguments()[0];
			Type param = type.getActualTypeArguments()[0];
			return ClassUtils.resolveClassName(param.getTypeName(),
					registry.getClass().getClassLoader());
		}

		private Class<?> findType(String name) {
			return findType((RootBeanDefinition) registry.getBeanDefinition(name));
		}

	}
}

abstract class ProxyWrapper<T> {

	private ObjectMapper mapper;

	private T delegate;

	private Class<?> type;

	public ProxyWrapper(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public void setDelegate(T delegate) {
		this.delegate = delegate;
	}

	public T getDelegate() {
		return delegate;
	}

	public void setType(Class<?> type) {
		this.type = type;
	}

	public Class<?> getType() {
		return this.type;
	}

	public Object fromJson(String value) {
		try {
			return mapper.readValue(value, getType());
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert from JSON: " + value);
		}
	}

	public String toJson(Object value) {
		try {
			return mapper.writeValueAsString(value);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert to JSON: " + value);
		}
	}

	@Override
	public String toString() {
		return "ProxyWrapper [delegate=" + delegate + ", type=" + type + "]";
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
