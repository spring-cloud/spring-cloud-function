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
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
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
	public FunctionCatalog functionCatalog() {
		return new ApplicationContextFunctionCatalog(functions, consumers, suppliers);
	}

	@Component
	public static class ContextFunctionPostProcessor
			implements BeanFactoryPostProcessor, BeanDefinitionRegistryPostProcessor {

		private BeanDefinitionRegistry registry;

		private BeanDefinitionRegistry targets = new SimpleBeanDefinitionRegistry();

		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
				throws BeansException {
			this.registry = registry;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory factory)
				throws BeansException {
			for (String name : factory.getBeanDefinitionNames()) {
				if (isGenericFunction(factory, name)) {
					wrapFunction(factory, name);
				}
				else if (isGenericSupplier(factory, name)) {
					wrapSupplier(factory, name);
				}
				else if (isGenericConsumer(factory, name)) {
					wrapConsumer(factory, name);
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

		private void wrapFunction(ConfigurableListableBeanFactory factory, String name) {
			AbstractBeanDefinition wrapped = getInputAwareWrappedBean(name,
					ProxyFunction.class).getBeanDefinition();
			registry.registerBeanDefinition(name, wrapped);
		}

		private void wrapSupplier(ConfigurableListableBeanFactory factory, String name) {
			AbstractBeanDefinition wrapped = getWrappedBean(name, ProxySupplier.class)
					.getBeanDefinition();
			registry.registerBeanDefinition(name, wrapped);
		}

		private void wrapConsumer(ConfigurableListableBeanFactory factory, String name) {
			AbstractBeanDefinition wrapped = getInputAwareWrappedBean(name,
					ProxyConsumer.class).getBeanDefinition();
			registry.registerBeanDefinition(name, wrapped);
		}

		private BeanDefinitionBuilder getInputAwareWrappedBean(String name,
				Class<?> type) {
			BeanDefinitionBuilder builder = getWrappedBean(name, type);
			builder.addPropertyValue("name", name);
			targets.registerBeanDefinition(name, registry.getBeanDefinition(name));
			builder.addPropertyValue("registry", targets);
			return builder;
		}

		private BeanDefinitionBuilder getWrappedBean(String name, Class<?> type) {
			BeanDefinition definition = registry.getBeanDefinition(name);
			BeanDefinitionBuilder builder = BeanDefinitionBuilder
					.genericBeanDefinition(type);
			builder.addPropertyValue("delegate", definition);
			return builder;
		}
	}
}

abstract class ProxyWrapper<S, T> {

	private ObjectMapper mapper;

	private T delegate;

	private String name;

	private Class<S> type;

	private BeanDefinitionRegistry registry;

	public ProxyWrapper(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public void setDelegate(T delegate) {
		this.delegate = delegate;
	}

	public T getDelegate() {
		return delegate;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setRegistry(BeanDefinitionRegistry registry) {
		this.registry = registry;
	}

	private Class<S> findType(RootBeanDefinition definition) {
		StandardMethodMetadata source = (StandardMethodMetadata) definition.getSource();
		ParameterizedType type = (ParameterizedType) (source.getIntrospectedMethod()
				.getGenericReturnType());
		type = (ParameterizedType) type.getActualTypeArguments()[0];
		Type param = type.getActualTypeArguments()[0];
		@SuppressWarnings("unchecked")
		Class<S> resolved = (Class<S>) ClassUtils.resolveClassName(param.getTypeName(),
				registry.getClass().getClassLoader());
		return resolved;
	}

	public Class<S> getType() {
		if (type == null) {
			type = findType((RootBeanDefinition) registry.getBeanDefinition(name));
		}
		return type;
	}

	public S fromJson(String value) {
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

}

class ProxyFunction extends ProxyWrapper<Object, Function<Flux<Object>, Flux<Object>>>
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

class ProxySupplier extends ProxyWrapper<Object, Supplier<Flux<Object>>>
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

class ProxyConsumer extends ProxyWrapper<Object, Consumer<Flux<Object>>>
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
