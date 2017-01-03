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

		private void wrapFunction(ConfigurableListableBeanFactory factory, String name) {
			BeanDefinition definition = registry.getBeanDefinition(name);
			BeanDefinitionBuilder builder = BeanDefinitionBuilder
					.genericBeanDefinition(ProxyFunction.class);
			builder.addPropertyValue("delegate", definition);
			builder.addPropertyValue("name", name);
			targets.registerBeanDefinition(name, definition);
			builder.addPropertyValue("registry", targets);
			registry.registerBeanDefinition(name, builder.getRawBeanDefinition());
		}

		private void wrapSupplier(ConfigurableListableBeanFactory factory, String name) {
			BeanDefinition definition = registry.getBeanDefinition(name);
			BeanDefinitionBuilder builder = BeanDefinitionBuilder
					.genericBeanDefinition(ProxySupplier.class);
			builder.addPropertyValue("delegate", definition);
			builder.addPropertyValue("name", name);
			targets.registerBeanDefinition(name, definition);
			builder.addPropertyValue("registry", targets);
			registry.registerBeanDefinition(name, builder.getRawBeanDefinition());
		}
	}
}

class ProxyFunction implements Function<Flux<String>, Flux<String>> {

	private ObjectMapper mapper;

	private Function<Flux<Object>, Flux<Object>> delegate;

	private String name;

	private Class<?> type;

	private BeanDefinitionRegistry registry;

	@Autowired
	public ProxyFunction(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public void setDelegate(Function<Flux<Object>, Flux<Object>> delegate) {
		this.delegate = delegate;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setRegistry(BeanDefinitionRegistry registry) {
		this.registry = registry;
	}

	private Class<?> findType(RootBeanDefinition definition) {
		StandardMethodMetadata source = (StandardMethodMetadata) definition.getSource();
		ParameterizedType type = (ParameterizedType) (source.getIntrospectedMethod()
				.getGenericReturnType());
		type = (ParameterizedType) type.getActualTypeArguments()[0];
		Type param = type.getActualTypeArguments()[0];
		return ClassUtils.resolveClassName(param.getTypeName(),
				registry.getClass().getClassLoader());
	}

	@Override
	public Flux<String> apply(Flux<String> input) {
		if (type == null) {
			type = findType((RootBeanDefinition) registry.getBeanDefinition(name));
		}
		return delegate.apply(input.map(value -> {
			try {
				return mapper.readValue(value, type);
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot convert from JSON: " + input);
			}
		})).map(value -> {
			try {
				return mapper.writeValueAsString(value);
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot convert to JSON: " + input);
			}
		});
	}

}

class ProxySupplier implements Supplier<Flux<String>> {

	private ObjectMapper mapper;

	private Supplier<Flux<Object>> delegate;

	private String name;

	private Class<?> type;

	private BeanDefinitionRegistry registry;

	@Autowired
	public ProxySupplier(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public void setDelegate(Supplier<Flux<Object>> delegate) {
		this.delegate = delegate;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setRegistry(BeanDefinitionRegistry registry) {
		this.registry = registry;
	}

	private Class<?> findType(RootBeanDefinition definition) {
		StandardMethodMetadata source = (StandardMethodMetadata) definition.getSource();
		ParameterizedType type = (ParameterizedType) (source.getIntrospectedMethod()
				.getGenericReturnType());
		type = (ParameterizedType) type.getActualTypeArguments()[0];
		Type param = type.getActualTypeArguments()[0];
		return ClassUtils.resolveClassName(param.getTypeName(),
				registry.getClass().getClassLoader());
	}

	@Override
	public Flux<String> get() {
		if (type == null) {
			type = findType((RootBeanDefinition) registry.getBeanDefinition(name));
		}
		return delegate.get().map(value -> {
			try {
				return mapper.writeValueAsString(value);
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot convert to JSON: " + value);
			}
		});
	}

}
