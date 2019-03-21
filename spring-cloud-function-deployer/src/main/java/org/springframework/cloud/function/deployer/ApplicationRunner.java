/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.cloud.function.deployer;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.thin.ArchiveUtils;
import org.springframework.boot.loader.thin.DependencyResolver;
import org.springframework.cloud.deployer.thin.ContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.SpringVersion;
import org.springframework.messaging.Message;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
// NOT a @Component (to prevent it from being scanned by the "main" application).
public class ApplicationRunner implements CommandLineRunner {

	private static final String DEFAULT_REACTOR_VERSION = "3.0.4.RELEASE";

	private static final String DEFAULT_SPRING_VERSION = SpringVersion.getVersion();

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

	@Override
	public void run(String... args) {
		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
		try {
			ClassLoader classLoader = createClassLoader();
			ClassUtils.overrideThreadContextClassLoader(classLoader);
			Class<?> cls = classLoader.loadClass(ContextRunner.class.getName());
			this.app = cls.newInstance();
			runContext(DeployedFunctionApplication.class.getName(),
					Collections.emptyMap(), args);
		}
		catch (Exception e) {
			logger.error("Cannot deploy", e);
		}
		finally {
			ClassUtils.overrideThreadContextClassLoader(contextLoader);
		}
	}

	@PreDestroy
	public void close() {
		closeContext();
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
		DependencyResolver resolver = DependencyResolver.instance();
		String reactor = getReactorCoordinates();
		// spring-core is OK, spring-context is not, spring-messaging depends on
		// spring-context (so it is not OK)
		String spring = getSpringCoordinates();
		List<File> resolved = Arrays.asList(
				resolver.resolve(new Dependency(new DefaultArtifact(reactor), "runtime")),
				resolver.resolve(new Dependency(new DefaultArtifact(spring), "runtime")));
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

	private String getSpringCoordinates() {
		Package pkg = Message.class.getPackage();
		String version = null;
		version = (pkg != null ? pkg.getImplementationVersion() : DEFAULT_SPRING_VERSION);
		return "org.springframework:spring-core:" + version;
	}

	private String getReactorCoordinates() {
		Package pkg = Flux.class.getPackage();
		String version = null;
		version = (pkg != null ? pkg.getImplementationVersion()
				: DEFAULT_REACTOR_VERSION);
		if (version == null) {
			Archive archive = ArchiveUtils.getArchive(Flux.class);
			try {
				String path = archive.getUrl().toString();
				if (path.endsWith("!/")) {
					path = path.substring(0, path.length() - 2);
				}
				path = StringUtils.getFilename(path);
				if (path.startsWith("reactor-core-")) {
					path = path.substring("reactor-core-".length());
				}
				if (path.endsWith(".jar")) {
					path = path.substring(0, path.length() - ".jar".length());
				}
				version = path;
			}
			catch (MalformedURLException e) {
				// ignore
			}
		}
		if (version == null) {
			version = DEFAULT_REACTOR_VERSION;
		}
		return "io.projectreactor:reactor-core:" + version;
	}

}
