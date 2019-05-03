/*
 * Copyright 2016-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context.config;

import java.util.ArrayList;
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
import org.springframework.beans.factory.SmartInitializingSingleton;
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
import org.springframework.cloud.function.context.catalog.FunctionInspector;
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
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 * @author Anshul Mehra
 */
@Configuration
@ConditionalOnMissingBean(FunctionCatalog.class)
@ComponentScan(basePackages = "${spring.cloud.function.scan.packages:functions}", //
		includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
				Supplier.class, Function.class, Consumer.class }))
public class ContextFunctionCatalogAutoConfiguration {

	static final String PREFERRED_MAPPER_PROPERTY = "spring.http.converters.preferred-json-mapper";

	@Bean
	public FunctionRegistry functionCatalog() {
		return new BeanFactoryFunctionCatalog();
	}

	@Bean(RoutingFunction.FUNCTION_NAME)
	@ConditionalOnProperty(name = "spring.cloud.function.routing.enabled", havingValue = "true")
	RoutingFunction gateway(FunctionCatalog functionCatalog, FunctionInspector functionInspector) {
		Collection<MessageConverter> messageConverters = new ArrayList<MessageConverter>();
		messageConverters.add(new MappingJackson2MessageConverter());
		messageConverters.add(new StringMessageConverter());
		messageConverters.add(new ByteArrayMessageConverter());
		CompositeMessageConverter messageConverter = new CompositeMessageConverter(messageConverters);
		return new RoutingFunction(functionCatalog, functionInspector, messageConverter);
	}

	protected static class BeanFactoryFunctionCatalog
			extends AbstractComposableFunctionRegistry
		implements SmartInitializingSingleton, BeanFactoryAware {

		private ApplicationEventPublisher applicationEventPublisher;

		private ConfigurableListableBeanFactory beanFactory;

		/**
		 * Will collect all suppliers, functions, consumers and function registration as
		 * late as possible in the lifecycle.
		 */
		@SuppressWarnings("rawtypes")
		@Override
		public void afterSingletonsInstantiated() {
			Map<String, Supplier> supplierBeans = this.beanFactory
				.getBeansOfType(Supplier.class);
			Map<String, Function> functionBeans = this.beanFactory
				.getBeansOfType(Function.class);
			Map<String, Consumer> consumerBeans = this.beanFactory
				.getBeansOfType(Consumer.class);
			Map<String, FunctionRegistration> functionRegistrationBeans = this.beanFactory
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
				if (this.hasSuppliers()) {
					this.applicationEventPublisher
							.publishEvent(new FunctionUnregistrationEvent(this,
									Supplier.class, this.getSupplierNames()));
				}
			}
		}

		@Override
		protected FunctionType findType(FunctionRegistration<?> functionRegistration, String name) {
			FunctionType functionType = super.findType(functionRegistration, name);
			if (functionType == null) {
				functionType = functionByNameExist(name)
						? new FunctionType(functionRegistration.getTarget().getClass())
						: new FunctionType(
								FunctionContextUtils.findType(name, this.beanFactory));
			}

			return functionType;
		}

		// @checkstyle:off
		/**
		 * @param initial a registration
		 * @param consumers consumers to register
		 * @param suppliers suppliers to register
		 * @param functions functions to register
		 * @return a new registration
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

			registrations.forEach(registration -> register(registration,
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
	@ConditionalOnProperty(name = ContextFunctionCatalogAutoConfiguration.PREFERRED_MAPPER_PROPERTY, //
			havingValue = "jackson", matchIfMissing = true)
	protected static class JacksonConfiguration {

		@Bean
		public JacksonMapper jsonMapper(ObjectMapper mapper) {
			return new JacksonMapper(mapper);
		}

	}

}
