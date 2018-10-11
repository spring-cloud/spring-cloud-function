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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.util.StringUtils;

/**
 * Utility class to launch a Spring Boot application (optionally) in an isolated class
 * loader. The class loader is created in such a way that it is mostly a copy of the
 * current class loader (i.e. the one that loaded this class), but has a parent containing
 * reactor-core (if present). It can then share the reactor dependency with other class
 * loaders that the app itself creates, without any other classes being shared, other than
 * the core JDK.
 * 
 * @author Mark Fisher
 * @author Dave Syer
 */
public class ApplicationBootstrap {

	private static Log logger = LogFactory.getLog(ApplicationBootstrap.class);
	private ApplicationRunner runner;
	private URLClassLoader classLoader;

	/**
	 * Run the provided main class as a Spring Boot application with the provided command
	 * line arguments.
	 * 
	 * @param mainClass the main class
	 * @param args the command line arguments
	 */
	public void run(Class<?> mainClass, String... args) {
		if (ApplicationBootstrap.isolated(args)) {
			runner(mainClass).run(args);
		}
		else {
			SpringApplication.run(mainClass, args);
		}
	}

	/**
	 * Clean up the resources used by this instance, if any. Called automatically on a
	 * runtime shutdown hook.
	 */
	public void close() {
		if (this.runner != null) {
			this.runner.close();
			this.runner = null;
		}
		if (this.classLoader != null) {
			try {
				this.classLoader.close();
			}
			catch (IOException e) {
				throw new IllegalStateException("Cannot close ClassLoader", e);
			}
			finally {
				this.classLoader = null;
			}
		}
	}

	public ApplicationRunner getRunner() {
		return this.runner;
	}

	private ApplicationRunner runner(Class<?> mainClass) {
		if (this.runner == null) {
			synchronized (this) {
				if (this.runner == null) {
					this.classLoader = createClassLoader(mainClass);
					this.runner = new ApplicationRunner(this.classLoader,
							mainClass.getName());
					Runtime.getRuntime().addShutdownHook(new Thread(this::close));
				}
			}
		}
		return this.runner;
	}

	private static boolean isolated(String[] args) {
		for (String arg : args) {
			if (arg.equals("--function.runner.isolated=false")) {
				return false;
			}
		}
		return true;
	}

	private URLClassLoader createClassLoader(Class<?> mainClass) {
		URL[] urls = findClassPath(mainClass);
		if (urls.length == 1) {
			URL[] classpath = extractClasspath(urls[0]);
			if (classpath != null) {
				urls = classpath;
			}
		}
		List<URL> child = new ArrayList<>();
		List<URL> parent = new ArrayList<>();
		for (URL url : urls) {
			child.add(url);
		}
		for (URL url : urls) {
			if (isRoot(StringUtils.getFilename(clean(url.toString())))) {
				parent.add(url);
				child.remove(url);
			}
		}
		logger.debug("Parent: " + parent);
		logger.debug("Child: " + child);
		ClassLoader base = getClass().getClassLoader();
		if (!parent.isEmpty()) {
			base = new URLClassLoader(parent.toArray(new URL[0]), base.getParent());
		}
		return new URLClassLoader(child.toArray(new URL[0]), base);
	}

	private URL[] findClassPath(Class<?> mainClass) {
		ClassLoader base = mainClass.getClassLoader();
		if (!(base instanceof URLClassLoader)) {
			try {
				// Guess the classpath, based on where we can resolve existing resources
				List<URL> list = Collections
						.list(mainClass.getClassLoader().getResources("META-INF"));
				List<URL> result = new ArrayList<>();
				result.add(mainClass.getProtectionDomain().getCodeSource().getLocation());
				for (URL url : list) {
					String path = url.toString();
					path = path.substring(0, path.length() - "/META-INF".length());
					if (path.endsWith("!")) {
						path = path + "/";
					}
					result.add(new URL(path));
				}
				return result.toArray(new URL[result.size()]);
			}
			catch (IOException e) {
				throw new IllegalStateException("Cannot find class path", e);
			}
		}
		else {
			@SuppressWarnings("resource")
			URLClassLoader urlClassLoader = (URLClassLoader) base;
			return urlClassLoader.getURLs();
		}
	}

	private boolean isRoot(String file) {
		return file.startsWith("reactor-core") || file.startsWith("reactive-streams");
	}

	private String clean(String jar) {
		// This works with fat jars like Spring Boot where the path elements look like
		// jar:file:...something.jar!/.
		return jar.endsWith("!/") ? jar.substring(0, jar.length() - 2) : jar;
	}

	private URL[] extractClasspath(URL url) {
		// This works for a jar indirection like in surefire and IntelliJ
		if (url.toString().endsWith(".jar")) {
			JarFile jar;
			try {
				jar = new JarFile(new File(url.toURI()));
				String path = jar.getManifest().getMainAttributes()
						.getValue("Class-Path");
				if (path != null) {
					List<URL> result = new ArrayList<>();
					for (String element : path.split(" ")) {
						result.add(new URL(element));
					}
					return result.toArray(new URL[0]);
				}
			}
			catch (Exception e) {
			}
		}
		return null;
	}
}
