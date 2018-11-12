/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.deployer;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Utility class for starting a Spring Boot application in a separate thread. Best used
 * from an isolated class loader, e.g. through {@link ApplicationRunner}.
 * 
 * @author Dave Syer
 */
public class ContextRunner {

	private ConfigurableApplicationContext context;
	private Thread runThread;
	private volatile boolean running = false;
	private Throwable error;
	private long timeout = 120000;

	public void run(final String source, final Map<String, Object> properties,
			final String... args) {
		// Run in new thread to ensure that the context classloader is setup
		this.runThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					resetUrlHandler();
					StandardEnvironment environment = new StandardEnvironment();
					environment.getPropertySources().addAfter(
							StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
							new MapPropertySource("appDeployer", properties));
					if (args != null && args.length > 0) {
						environment.getPropertySources().addFirst(
								new SimpleCommandLinePropertySource("args", args));
					}
					running = true;
					Class<?> sourceClass = ClassUtils.resolveClassName(source, null);
					SpringApplication builder = builder(sourceClass, environment);
					context = builder.run(args);
				}
				catch (Throwable ex) {
					error = ex;
				}

			}
		});
		this.runThread.start();
		try {
			this.runThread.join(timeout);
			this.running = context != null && context.isRunning();
		}
		catch (InterruptedException e) {
			this.running = false;
			Thread.currentThread().interrupt();
		}

	}

	public void close() {
		if (this.context != null) {
			this.context.close();
			resetUrlHandler();
		}
		// TODO: JDBC leak protection?
		this.running = false;
		this.runThread.setContextClassLoader(null);
		this.runThread = null;
	}

	public ConfigurableApplicationContext getContext() {
		return this.context;
	}

	private void resetUrlHandler() {
		if (ClassUtils.isPresent(
				"org.apache.catalina.webresources.TomcatURLStreamHandlerFactory", null)) {
			setField(ClassUtils.resolveClassName(
					"org.apache.catalina.webresources.TomcatURLStreamHandlerFactory",
					null), "instance", null);
			setField(URL.class, "factory", null);
		}
	}

	private void setField(Class<?> type, String name, Object value) {
		Field field = ReflectionUtils.findField(type, name);
		ReflectionUtils.makeAccessible(field);
		ReflectionUtils.setField(field, null, value);
	}

	public boolean isRunning() {
		return running;
	}

	public Throwable getError() {
		return this.error;
	}

	private SpringApplication builder(Class<?> type, StandardEnvironment environment) {
		SpringApplication application;
		if (!isFunctional(environment)) {
			application = new SpringApplication(type);
		}
		else {
			application = FunctionalSpringApplicationCreator.create(type);
		}
		application.setEnvironment(environment);
		application.setRegisterShutdownHook(false);
		return application;
	}

	private static boolean isFunctional(StandardEnvironment environment) {
		if (!ClassUtils.isPresent(
				"org.springframework.cloud.function.context.FunctionalSpringApplication",
				null)) {
			return false;
		}
		return environment.resolvePlaceholders("${spring.functional.enabled:true}")
				.equals("true");
	}
	
	private static class FunctionalSpringApplicationCreator {

		public static SpringApplication create(Class<?> type) {
			return new FunctionalSpringApplication(type);
		}
		
	}
}
