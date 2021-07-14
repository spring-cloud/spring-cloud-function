/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.function.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import static java.util.Arrays.stream;

/**
 * @author Dave Syer
 * @author Semyon Fishman
 * @author Oleg Zhurakousky
 */
public class FunctionalSpringApplication
		extends org.springframework.boot.SpringApplication {

	/**
	 * Flag to say that context is functional beans.
	 */
	public static final String SPRING_FUNCTIONAL_ENABLED = "spring.functional.enabled";

	/**
	 * Enumeration of web application types.
	 */
	public static final String SPRING_WEB_APPLICATION_TYPE = "spring.main.web-application-type";

	/**
	 * Name of default property source.
	 */
	private static final String DEFAULT_PROPERTIES = "defaultProperties";

	public FunctionalSpringApplication(Class<?>... primarySources) {
		super(primarySources);
		setApplicationContextFactory(ApplicationContextFactory.ofContextClass(GenericApplicationContext.class));
		if (ClassUtils.isPresent("org.springframework.web.reactive.DispatcherHandler",
				null)) {
			setWebApplicationType(WebApplicationType.REACTIVE);
		}
		else {
			setWebApplicationType(WebApplicationType.NONE);
		}
	}

	public static void main(String[] args) throws Exception {
		FunctionalSpringApplication.run(new Class<?>[0], args);
	}

	public static ConfigurableApplicationContext run(Class<?> primarySource,
			String... args) {
		return run(new Class<?>[] { primarySource }, args);
	}

	public static ConfigurableApplicationContext run(Class<?>[] primarySources,
			String[] args) {
		return new FunctionalSpringApplication(primarySources).run(args);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
		super.postProcessApplicationContext(context);
		boolean functional = false;
		Assert.isInstanceOf(GenericApplicationContext.class, context,
				"ApplicationContext must be an instanceof GenericApplicationContext");
		for (Object source : getAllSources()) {
			Class<?> type = null;
			Object handler = null;
			if (source instanceof String) {
				String name = (String) source;
				if (ClassUtils.isPresent(name, null)) {
					type = ClassUtils.resolveClassName(name, null);
				}
			}
			else if (source instanceof Class<?>) {
				type = (Class<?>) source;
			}
			else {
				type = source.getClass();
				handler = source;
			}
			if (ApplicationContextInitializer.class.isAssignableFrom(type)) {
				if (handler == null) {
					handler = BeanUtils.instantiateClass(type);
				}

				ApplicationContextInitializer<ConfigurableApplicationContext> initializer =
						(ApplicationContextInitializer<ConfigurableApplicationContext>) handler;
				initializer.initialize(context);
				functional = true;
			}
			else if (Function.class.isAssignableFrom(type)
					|| Consumer.class.isAssignableFrom(type)
					|| Supplier.class.isAssignableFrom(type)) {
				Class<?> functionType = type;
				Object function = handler;
				if (source.equals(functionType)) {
					context.addBeanFactoryPostProcessor(beanFactory -> {
						BeanDefinitionRegistry bdRegistry = (BeanDefinitionRegistry) beanFactory;
						if (!ObjectUtils.isEmpty(context.getBeanNamesForType(functionType))) {
							stream(context.getBeanNamesForType(functionType))
							.forEach(beanName -> bdRegistry.registerAlias(beanName, "function"));
						}
						else {
							this.register((GenericApplicationContext) context, function, functionType);
						}
					});
				}
				else {
					this.register((GenericApplicationContext) context, function, functionType);
				}
				functional = true;
			}
		}
		if (functional) {
			defaultProperties(context);
		}
	}

	private void register(GenericApplicationContext context, Object function, Class<?> functionType) {
		context.registerBean("function", FunctionRegistration.class,
				() -> new FunctionRegistration<>(
						handler(context, function, functionType))
								.type(FunctionType.of(functionType)));
	}

	private Object handler(GenericApplicationContext generic, Object handler,
			Class<?> type) {
		if (handler == null) {
			handler = generic.getAutowireCapableBeanFactory().createBean(type);
		}
		return handler;
	}

	@Override
	protected void load(ApplicationContext context, Object[] sources) {
		if (!context.getEnvironment().getProperty(SPRING_FUNCTIONAL_ENABLED,
				Boolean.class, false)) {
			super.load(context, sources);
		}
	}

	private void defaultProperties(ConfigurableApplicationContext context) {
		MutablePropertySources sources = context.getEnvironment().getPropertySources();
		if (!sources.contains(DEFAULT_PROPERTIES)) {
			sources.addLast(
					new MapPropertySource(DEFAULT_PROPERTIES, Collections.emptyMap()));
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> source = (Map<String, Object>) sources.get(DEFAULT_PROPERTIES)
				.getSource();
		Map<String, Object> map = new HashMap<>(source);
		map.put(SPRING_FUNCTIONAL_ENABLED, "true");
		map.put(SPRING_WEB_APPLICATION_TYPE, getWebApplicationType());
		sources.replace(DEFAULT_PROPERTIES,
				new MapPropertySource(DEFAULT_PROPERTIES, map));
	}

}
