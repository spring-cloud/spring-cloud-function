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
package org.springframework.cloud.function.deployer;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.aether.graph.Dependency;

import org.springframework.boot.Banner.Mode;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.loader.thin.DependencyResolver;
import org.springframework.cloud.deployer.thin.ContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.LiveBeansView;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * @author Dave Syer
 *
 */
// NOT a @Component (to prevent it from being scanned by the "main" application).
public class ApplicationRunner implements CommandLineRunner {

	private static Log logger = LogFactory.getLog(ApplicationRunner.class);

	public static void main(String[] args) {
		new ApplicationRunner().start(args);
	}

	public ConfigurableApplicationContext start(String... args) {
		return new SpringApplicationBuilder(ApplicationRunner.class).web(false)
				.contextClass(AnnotationConfigApplicationContext.class)
				.bannerMode(Mode.OFF).properties("spring.main.applicationContextClass="
						+ AnnotationConfigApplicationContext.class.getName())
				.run(args);
	}

	private Object app;
	private ClassLoader classLoader;

	@Override
	public void run(String... args) {
		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
		try {
			this.classLoader = createClassLoader();
			ClassUtils.overrideThreadContextClassLoader(this.classLoader);
			Class<?> cls = this.classLoader.loadClass(ContextRunner.class.getName());
			this.app = cls.newInstance();
			runContext(DeployedFunctionApplication.class.getName(), Collections
					.singletonMap(LiveBeansView.MBEAN_DOMAIN_PROPERTY_NAME, "deployer"),
					args);
		}
		catch (Exception e) {
			logger.error("Cannot deploy", e);
		}
		finally {
			ClassUtils.overrideThreadContextClassLoader(contextLoader);
		}
		RuntimeException e = getError();
		if (e != null) {
			throw e;
		}
	}

	@PreDestroy
	public void close() throws IOException {
		closeContext();
		if (this.classLoader!=null && this.classLoader instanceof Closeable) {
			((Closeable) this.classLoader).close();
		}
	}

	private RuntimeException getError() {
		if (this.app == null) {
			return null;
		}
		Method method = ReflectionUtils.findMethod(this.app.getClass(), "getError");
		Throwable e;
		e = (Throwable) ReflectionUtils.invokeMethod(method, this.app);
		if (e==null) {
			return null;
		}
		if (e instanceof RuntimeException) {
			return (RuntimeException) e;
		}
		return new IllegalStateException("Cannot launch", e);
	}

	private void runContext(String mainClass, Map<String, String> properties,
			String... args) {
		Method method = ReflectionUtils.findMethod(this.app.getClass(), "run",
				String.class, Map.class, String[].class);
		ReflectionUtils.invokeMethod(method, this.app, mainClass, properties, args);
	}

	private void closeContext() {
		Method method = ReflectionUtils.findMethod(this.app.getClass(), "close");
		ReflectionUtils.invokeMethod(method, this.app);
	}

	private ClassLoader createClassLoader() {
		ClassLoader base = getClass().getClassLoader();
		if (!(base instanceof URLClassLoader)) {
			throw new IllegalStateException("Need a URL class loader, found: " + base);
		}
		@SuppressWarnings("resource")
		URLClassLoader urlClassLoader = (URLClassLoader) base;
		URL[] urls = urlClassLoader.getURLs();
		List<URL> child = new ArrayList<>();
		List<URL> parent = new ArrayList<>();
		for (URL url : urls) {
			child.add(url);
		}
		List<File> resolved = resolveParent();
		for (File archive : resolved) {
			try {
				URL url = archive.toURI().toURL();
				parent.add(url);
				child.remove(url);
			}
			catch (MalformedURLException e) {
				throw new IllegalStateException("Cannot locate jar for: " + archive);
			}
		}
		logger.info("Parent: " + parent);
		logger.info("Child: " + child);
		if (!parent.isEmpty()) {
			base = new URLClassLoader(parent.toArray(new URL[0]), base.getParent());
		}
		return new URLClassLoader(child.toArray(new URL[0]), base);
	}

	private List<File> resolveParent() {
		DependencyResolver resolver = DependencyResolver.instance();
		List<Dependency> dependencies = resolver
				.dependencies(new ClassPathResource("core-pom.xml"));
		List<File> resolved = new ArrayList<>();
		for (Dependency dependency : dependencies) {
			resolved.add(resolver.resolve(dependency));
		}
		return resolved;
	}

}
