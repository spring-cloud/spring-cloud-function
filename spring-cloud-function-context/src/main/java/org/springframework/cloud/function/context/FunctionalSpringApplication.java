/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.cloud.function.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.BeanUtils;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.ClassUtils;

/**
 * @author Dave Syer
 *
 */
public class FunctionalSpringApplication
		extends org.springframework.boot.SpringApplication {

	/**
	 * Name of default property source.
	 */
	private static final String DEFAULT_PROPERTIES = "defaultProperties";

	/**
	 * Flag to say that context is functional beans.
	 */
	public static final String SPRING_FUNCTIONAL_ENABLED = "spring.functional.enabled";

	/**
	 * Enumeration of web application types.
	 */
	public static final String SPRING_WEB_APPLICATION_TYPE = "spring.main.web-application-type";

	public static ConfigurableApplicationContext run(Class<?> primarySource,
			String... args) {
		return run(new Class<?>[] { primarySource }, args);
	}

	public static ConfigurableApplicationContext run(Class<?>[] primarySources,
			String[] args) {
		return new FunctionalSpringApplication(primarySources).run(args);
	}

	public FunctionalSpringApplication(Class<?>... primarySources) {
		super(primarySources);
		setApplicationContextClass(GenericApplicationContext.class);
		if (ClassUtils.isPresent("org.springframework.web.reactive.DispatcherHandler",
				null)) {
			setWebApplicationType(WebApplicationType.REACTIVE);
		} else {
			setWebApplicationType(WebApplicationType.NONE);
		}
	}

	@Override
	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
		super.postProcessApplicationContext(context);
		boolean functional = false;
		if (context instanceof GenericApplicationContext) {
			GenericApplicationContext generic = (GenericApplicationContext) context;
			for (Object source : getAllSources()) {
				if (source instanceof Class<?>) {
					Class<?> type = (Class<?>) source;
					if (ApplicationContextInitializer.class.isAssignableFrom(type)) {
						@SuppressWarnings("unchecked")
						ApplicationContextInitializer<GenericApplicationContext> initializer = BeanUtils
								.instantiateClass(type,
										ApplicationContextInitializer.class);
						initializer.initialize(generic);
						functional = true;
					}
					else if (Function.class.isAssignableFrom(type)
							|| Consumer.class.isAssignableFrom(type)
							|| Supplier.class.isAssignableFrom(type)) {
						generic.registerBean("function", FunctionRegistration.class,
								() -> new FunctionRegistration<>(context
										.getAutowireCapableBeanFactory().createBean(type))
												.type(FunctionType.of(type)));
						functional = true;
					}
				}
				else {
					Class<?> type = source.getClass();
					if (ApplicationContextInitializer.class.isAssignableFrom(type)) {
						@SuppressWarnings("unchecked")
						ApplicationContextInitializer<GenericApplicationContext> initializer = (ApplicationContextInitializer<GenericApplicationContext>) source;
						initializer.initialize(generic);
						functional = true;
					}
					else if (Function.class.isAssignableFrom(type)
							|| Consumer.class.isAssignableFrom(type)
							|| Supplier.class.isAssignableFrom(type)) {
						generic.registerBean("function", FunctionRegistration.class,
								() -> new FunctionRegistration<>(source)
										.type(FunctionType.of(type)));
						functional = true;
					}
				}
			}
			if (functional) {
				defaultProperties(generic);
			}
		}
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
