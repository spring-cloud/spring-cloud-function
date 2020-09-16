/*
 * Copyright 2019-2020 the original author or authors.
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

package org.springframework.cloud.function.context.catalog;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;


/**
 * Implementation of {@link FunctionRegistry} and {@link FunctionCatalog} which is aware of the
 * underlying {@link BeanFactory} to access available functions. Functions that are registered via
 * {@link #register(FunctionRegistration)} operation are stored/cached locally.
 *
 * @author Oleg Zhurakousky
 * @author Eric Botard
 * @since 3.0
 */
public class BeanFactoryAwareFunctionRegistry extends SimpleFunctionRegistry implements ApplicationContextAware, InitializingBean {

	private ConfigurableApplicationContext applicationContext;

	public BeanFactoryAwareFunctionRegistry(ConversionService conversionService,
		@Nullable CompositeMessageConverter messageConverter) {
		super(conversionService, messageConverter);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		String userDefinition = this.applicationContext.getEnvironment().getProperty("spring.cloud.function.definition");
		init(userDefinition);
	}

	@Override
	public int size() {
		return this.applicationContext.getBeanNamesForType(Supplier.class).length +
			this.applicationContext.getBeanNamesForType(Function.class).length +
			this.applicationContext.getBeanNamesForType(Consumer.class).length;
	}

	@Override
	public Set<String> getNames(Class<?> type) {
		Set<String> registeredNames = super.getNames(type);
		if (type == null) {
			registeredNames
				.addAll(Arrays.asList(this.applicationContext.getBeanNamesForType(Function.class)));
			registeredNames
				.addAll(Arrays.asList(this.applicationContext.getBeanNamesForType(Supplier.class)));
			registeredNames
				.addAll(Arrays.asList(this.applicationContext.getBeanNamesForType(Consumer.class)));
		}
		else {
			registeredNames.addAll(Arrays.asList(this.applicationContext.getBeanNamesForType(type)));
		}
		return registeredNames;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	@Override
	Object locateFunction(String name) {
		Object function = super.locateFunction(name);
		if (function == null) {
			try {
				function = BeanFactoryAnnotationUtils.qualifiedBeanOfType(this.applicationContext.getBeanFactory(), Object.class, name);
			}
			catch (Exception e) {
				// ignore
			}
		}
		if (function == null && this.applicationContext.containsBean(name)) {
			function = this.applicationContext.getBean(name);
		}

		if (function != null && this.notFunction(function.getClass())
			&& this.applicationContext
			.containsBean(name + FunctionRegistration.REGISTRATION_NAME_SUFFIX)) { // e.g., Kotlin lambdas
			function = this.applicationContext
				.getBean(name + FunctionRegistration.REGISTRATION_NAME_SUFFIX, FunctionRegistration.class);
		}
		return function;
	}

	@Override
	Type discoverFunctionType(Object function, String... names) {
		if (function instanceof RoutingFunction) {
			return FunctionType.of(FunctionContextUtils.findType(applicationContext.getBeanFactory(), names)).getType();
		}
		else if (function instanceof FunctionRegistration) {
			return ((FunctionRegistration) function).getType().getType();
		}
		boolean beanDefinitionExists = false;
		for (int i = 0; i < names.length && !beanDefinitionExists; i++) {
			beanDefinitionExists = this.applicationContext.getBeanFactory().containsBeanDefinition(names[i]);
			if (this.applicationContext.containsBean("&" + names[i])) {
				Class<?> objectType = this.applicationContext.getBean("&" + names[i], FactoryBean.class)
					.getObjectType();
				return FunctionTypeUtils.discoverFunctionTypeFromClass(objectType);
			}
		}
		if (!beanDefinitionExists) {
			logger.info("BeanDefinition for function name(s) '" + Arrays.asList(names) +
				"' can not be located. FunctionType will be based on " + function.getClass());
		}

		Type type = FunctionTypeUtils.discoverFunctionTypeFromClass(function.getClass());
		if (beanDefinitionExists) {
			Type t = FunctionTypeUtils.getImmediateGenericType(type, 0);
			if (t == null || t == Object.class) {
				type = FunctionType.of(FunctionContextUtils.findType(this.applicationContext.getBeanFactory(), names)).getType();
			}
		}
		return type;
	}

	@Override
	String discoverDefaultDefinitionIfNecessary(String definition) {
		if (StringUtils.isEmpty(definition) || definition.endsWith("|")) {
			// the underscores are for Kotlin function registrations (see KotlinLambdaToFunctionAutoConfiguration)
			String[] functionNames = Stream.of(this.applicationContext.getBeanNamesForType(Function.class))
				.filter(n -> !n.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX) && !n
					.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);
			String[] consumerNames = Stream.of(this.applicationContext.getBeanNamesForType(Consumer.class))
				.filter(n -> !n.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX) && !n
					.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);
			String[] supplierNames = Stream.of(this.applicationContext.getBeanNamesForType(Supplier.class))
				.filter(n -> !n.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX) && !n
					.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);

			/*
			 * we may need to add BiFunction and BiConsumer at some point
			 */
			List<String> names = Stream
				.concat(Stream.of(functionNames), Stream.concat(Stream.of(consumerNames), Stream.of(supplierNames)))
				.collect(Collectors.toList());

			if (definition.endsWith("|")) {
				Set<String> fNames = this.getNames(null);
				definition = this.determinImpliedDefinition(fNames, definition);
			}
			else if (!ObjectUtils.isEmpty(names)) {
				if (names.size() > 1) {
					logger.warn("Found more than one function bean in BeanFactory: " + names
						+ ". If you did not intend to use functions, ignore this message. However, if you did "
						+ "intend to use functions in the context of spring-cloud-function, consider "
						+ "providing 'spring.cloud.function.definition' property pointing to a function bean(s) "
						+ "you intend to use. For example, 'spring.cloud.function.definition=myFunction'");
					return null;
				}
				definition = names.get(0);
			}
			else {
				definition = this.discoverDefaultDefinitionFromRegistration();
			}

			if (StringUtils.hasText(definition) && this.applicationContext.containsBean(definition)) {
				Type functionType = discoverFunctionType(this.applicationContext.getBean(definition), definition);
				if (!FunctionTypeUtils.isSupplier(functionType) && !FunctionTypeUtils
					.isFunction(functionType) && !FunctionTypeUtils.isConsumer(functionType)) {
					logger.debug("Discovered functional instance of bean '" + definition + "' as a default function, however its "
							+ "function argument types can not be determined. Discarding.");
					definition = null;
				}
			}
		}
		if (!StringUtils.hasText(definition)) {
			String[] functionRegistrationNames = Stream.of(applicationContext.getBeanNamesForType(FunctionRegistration.class))
					.filter(n -> !n.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX) && !n
						.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);
			if (functionRegistrationNames != null) {
				if (functionRegistrationNames.length == 1) {
					definition = functionRegistrationNames[0];
				}
				else {
					logger.debug("Found more than one function registration bean in BeanFactory: " + functionRegistrationNames
							+ ". If you did not intend to use functions, ignore this message. However, if you did "
							+ "intend to use functions in the context of spring-cloud-function, consider "
							+ "providing 'spring.cloud.function.definition' property pointing to a function bean(s) "
							+ "you intend to use. For example, 'spring.cloud.function.definition=myFunction'");
				}
			}
		}
		return definition;
	}

	@Override
	Type discoverFunctionTypeByName(String name) {
		return FunctionContextUtils.findType(applicationContext.getBeanFactory(), name);
	}

	@Override
	Collection<String> getAliases(String key) {
		Collection<String> names = new LinkedHashSet<>();
		String value = getQualifier(key);
		if (value.equals(key) && this.applicationContext != null) {
			names.addAll(Arrays.asList(this.applicationContext.getBeanFactory().getAliases(key)));
		}
		names.add(value);
		return names;
	}

	private boolean notFunction(Class<?> functionClass) {
		return !Function.class.isAssignableFrom(functionClass)
			&& !Supplier.class.isAssignableFrom(functionClass)
			&& !Consumer.class.isAssignableFrom(functionClass);
	}

	private String getQualifier(String key) {
		if (this.applicationContext != null && this.applicationContext.getBeanFactory().containsBeanDefinition(key)) {
			BeanDefinition beanDefinition = this.applicationContext.getBeanFactory().getBeanDefinition(key);
			Object source = beanDefinition.getSource();
			if (source instanceof StandardMethodMetadata) {
				StandardMethodMetadata metadata = (StandardMethodMetadata) source;
				Qualifier qualifier = AnnotatedElementUtils.findMergedAnnotation(metadata.getIntrospectedMethod(),
					Qualifier.class);
				if (qualifier != null && qualifier.value().length() > 0) {
					return qualifier.value();
				}
			}
		}
		return key;
	}
}
