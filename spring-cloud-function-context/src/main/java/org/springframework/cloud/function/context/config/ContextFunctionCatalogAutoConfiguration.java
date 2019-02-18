/*
 * Copyright 2016-2019 the original author or authors.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
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
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.AbstractComposableFunctionRegistry;
import org.springframework.cloud.function.context.catalog.FunctionUnregistrationEvent;
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
@ComponentScan(
		basePackages = "${spring.cloud.function.scan.packages:functions}",
		includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
		classes = {
				Supplier.class,
				Function.class,
				Consumer.class }))
public class ContextFunctionCatalogAutoConfiguration {

	static final String PREFERRED_MAPPER_PROPERTY = "spring.http.converters.preferred-json-mapper";

	@Bean
	public FunctionRegistry functionCatalog() {
		return new BeanFactoryFunctionCatalog();
	}

	protected static class BeanFactoryFunctionCatalog extends AbstractComposableFunctionRegistry
		implements InitializingBean, BeanFactoryAware {

		private ApplicationEventPublisher applicationEventPublisher;

		private ConfigurableListableBeanFactory beanFactory;

		@Override
		public FunctionRegistration<?> getRegistration(Object function) {
			String functionName = this.lookupFunctionName(function);
			if (StringUtils.hasText(functionName)) {
				return new FunctionRegistration<>(function, functionName)
						.type(findType(function).getType());
			}
			return null;
		}

		public <T> void register(FunctionRegistration<T> functionRegistration) {
			Assert.notEmpty(functionRegistration.getNames(),
					"'registration' must contain at least one name before it is registered in catalog.");
			wrap(functionRegistration, functionRegistration.getNames().iterator().next());
		}

		/**
		 * Will collect all suppliers, functions, consumers and function registration as
		 * late as possible in the lifecycle.
		 */
		@Override
		@SuppressWarnings("rawtypes")
		public void afterPropertiesSet() throws Exception {
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

		@PreDestroy
		public void close() {
			if (this.applicationEventPublisher != null) {
				if (this.hasFunctions()) {
					this.applicationEventPublisher
							.publishEvent(new FunctionUnregistrationEvent(this,
									Function.class, this.getFunctionNames()));
				}
				if (this.hasConsumers()) {
					this.applicationEventPublisher
							.publishEvent(new FunctionUnregistrationEvent(this,
									Consumer.class, this.getConsumerNames()));
				}
				if (this.hasSuppliers()) {
					this.applicationEventPublisher
							.publishEvent(new FunctionUnregistrationEvent(this,
									Supplier.class, this.getSupplierNames()));
				}
			}
		}

		@Override
		protected FunctionType findType(Object function) {
			String name = this.lookupFunctionName(function);
			FunctionType functionType = this.getFunctionType(name);

			if (functionType == null) {
				functionType = functionByNameExist(name)
						? new FunctionType(function.getClass()) : new FunctionType(
								FunctionContextUtils.findType(name, this.beanFactory));
			}

			return functionType;
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

		private Collection<String> getAliases(String key) {
			Collection<String> names = new LinkedHashSet<>();
			String value = getQualifier(key);
			if (value.equals(key) && this.beanFactory != null) {
				names.addAll(Arrays.asList(this.beanFactory.getAliases(key)));
			}
			names.add(value);
			return names;
		}

//		private void wrap(FunctionRegistration<?> registration, String key) {
//			Object target = registration.getTarget();
////			this.addName(target, key);
//			if (registration.getType() != null) {
//				this.addType(key, registration.getType());
//			}
//			else {
//				registration.type(findType(target).getType());
//			}
//			Class<?> type;
//			registration = isolated(registration).wrap();
//			target = registration.getTarget();
//			if (target instanceof Supplier) {
//				type = Supplier.class;
//				for (String name : registration.getNames()) {
//					this.addSupplier(name, registration.getTarget());
//				}
//			}
//			else if (target instanceof Consumer) {
//				type = Consumer.class;
//				for (String name : registration.getNames()) {
//					this.addConsumer(name, registration.getTarget());
//				}
//			}
//			else if (target instanceof Function) {
//				type = Function.class;
//				for (String name : registration.getNames()) {
//					this.addFunction(name, registration.getTarget());
//				}
//			}
//			else {
//				return;
//			}
//			//this.addName(registration.getTarget(), key);
//			if (this.applicationEventPublisher != null) {
//				this.applicationEventPublisher.publishEvent(new FunctionRegistrationEvent(
//						registration.getTarget(), type, registration.getNames()));
//			}
//		}

//		@SuppressWarnings({ "rawtypes", "unchecked" })
//		private FunctionRegistration<?> isolated(FunctionRegistration<?> input) {
//			FunctionRegistration<Object> registration = (FunctionRegistration<Object>) input;
//			Object target = registration.getTarget();
//			boolean isolated = getClass().getClassLoader() != target.getClass()
//					.getClassLoader();
//			if (isolated) {
//				if (target instanceof Supplier<?> && isolated) {
//					target = new IsolatedSupplier((Supplier<?>) target);
//				}
//				else if (target instanceof Function<?, ?>) {
//					target = new IsolatedFunction((Function<?, ?>) target);
//				}
//				else if (target instanceof Consumer<?>) {
//					target = new IsolatedConsumer((Consumer<?>) target);
//				}
//			}
//
//			registration.target(target);
//			return registration;
//		}

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

		private boolean functionByNameExist(String name) {
			return name == null || this.beanFactory == null
					|| !this.beanFactory.containsBeanDefinition(name);
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
	@ConditionalOnProperty(
			name = ContextFunctionCatalogAutoConfiguration.PREFERRED_MAPPER_PROPERTY,
			havingValue = "jackson",
			matchIfMissing = true)
	protected static class JacksonConfiguration {

		@Bean
		public JacksonMapper jsonMapper(ObjectMapper mapper) {
			return new JacksonMapper(mapper);
		}

	}

}
