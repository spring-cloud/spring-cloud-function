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
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.core.FluxFunction;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StreamUtils;

/**
 *
 * Registers beans that will be picked up by spring-cloud-function-context magic. Sets up
 * infrastructure capable of instantiating a "functional" bean (whether Supplier, Function
 * or Consumer) loaded dynamically according to {@link FunctionProperties}.
 *
 * <p>
 * Resolves jar location provided by the user using a flexible ResourceLoader.
 * </p>
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Dave Syer
 */
@Configuration
class FunctionCreatorConfiguration {

	private static Log logger = LogFactory.getLog(FunctionCreatorConfiguration.class);

	@Autowired
	private FunctionRegistry registry;

	@Autowired
	private FunctionProperties properties;

	@Autowired
	private DelegatingResourceLoader delegatingResourceLoader;

	@Autowired
	private ConfigurableApplicationContext context;

	private BeanCreatorClassLoader functionClassLoader;

	private BeanCreator creator;

	/**
	 * Registers a function for each of the function classes passed into the
	 * {@link FunctionProperties}. They are named sequentially "function0", "function1",
	 * etc. The instances are created in an isolated class loader, so the jar they are
	 * packed in has to define all the dependencies (except core JDK).
	 */
	@PostConstruct
	public void init() {
		URL[] urls = Arrays.stream(properties.getLocation())
				.flatMap(toResourceURL(delegatingResourceLoader)).toArray(URL[]::new);

		try {
			logger.info(
					"Locating function from " + Arrays.asList(properties.getLocation()));
			this.creator = new BeanCreator(urls);
			this.creator.run(properties.getMain());
			Arrays.stream(functionNames()).map(this.creator::create).sequential()
					.forEach(this.creator::register);
			if (properties.getName().contains("|")) {
				// A composite function has to be explicitly registered before it is
				// looked up because we are using the SingleEntryFunctionRegistry
				this.registry.lookup(Consumer.class, properties.getName());
				this.registry.lookup(Function.class, properties.getName());
				this.registry.lookup(Supplier.class, properties.getName());
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot create functions", e);
		}
	}

	private String[] functionNames() {
		if (properties.getBean() != null && properties.getBean().length > 0) {
			return properties.getBean();
		}
		return this.creator.getFunctionNames();
	}

	@PreDestroy
	public void close() {
		if (this.creator != null) {
			this.creator.close();
		}
		if (this.functionClassLoader != null) {
			try {
				this.functionClassLoader.close();
				this.functionClassLoader = null;
				Runtime.getRuntime().gc();
			}
			catch (IOException e) {
				throw new IllegalStateException("Cannot close function class loader", e);
			}
		}
	}

	private Function<String, Stream<URL>> toResourceURL(
			DelegatingResourceLoader resourceLoader) {
		return l -> {
			if (l.equals("app:classpath")) {
				return Stream.of(urls());
			}
			try {
				return Stream.of(resourceLoader.getResource(l).getFile().toURI().toURL());
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		};
	}

	private URL[] urls() {
		if (getClass().getClassLoader() instanceof URLClassLoader) {
			return ((URLClassLoader) getClass().getClassLoader()).getURLs();
		}
		// Want to load these the test types in a disposable classloader:
		List<URL> urls = new ArrayList<>();
		String jcp = System.getProperty("java.class.path");
		StringTokenizer jcpEntries = new StringTokenizer(jcp, File.pathSeparator);
		while (jcpEntries.hasMoreTokens()) {
			String pathEntry = jcpEntries.nextToken();
			try {
				urls.add(new File(pathEntry).toURI().toURL());
			}
			catch (MalformedURLException e) {
			}
		}
		return urls.toArray(new URL[urls.size()]);
	}

	private class ComputeLauncher extends JarLauncher {

		public ComputeLauncher(Archive archive) {
			super(archive);
		}

		@Override
		public String getMainClass() throws Exception {
			try {
				return super.getMainClass();
			}
			catch (Exception e) {
				return null;
			}
		}

		public URL[] getClassLoaderUrls() throws Exception {
			List<Archive> archives = getClassPathArchives();
			if (archives.isEmpty()) {
				URL url = getArchive().getUrl();
				if (url.toString().contains(".jar")) { // Surefire or IntelliJ?
					URL[] classpath = extractClasspath(url.toString());
					if (classpath != null) {
						return classpath;
					}
				}
				return new URL[] { getArchive().getUrl() };
			}
			return archives.stream().map(archive -> {
				try {
					return archive.getUrl();
				}
				catch (MalformedURLException e) {
					throw new IllegalStateException("Bad URL: " + archive, e);
				}
			}).collect(Collectors.toList()).toArray(new URL[0]);
		}

		private URL[] extractClasspath(String url) {
			// This works for a jar indirection like in surefire and IntelliJ
			if (url.endsWith(".jar!/")) {
				url = url.substring(0, url.length() - "!/".length());
				if (url.startsWith("jar:")) {
					url = url.substring("jar:".length());
				}
				if (url.startsWith("file:")) {
					url = url.substring("file:".length());
				}
			}
			if (url.endsWith(".jar")) {
				JarFile jar;
				try {
					jar = new JarFile(new File(url));
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

	/**
	 * Encapsulates the bean and spring application context creation concerns for
	 * functions. Creates a single application context if <code>run()</code> is called
	 * with a non-null main class, and then uses it to lookup a function (by name and then
	 * by type).
	 */
	private class BeanCreator {

		private AtomicInteger counter = new AtomicInteger(0);

		private ApplicationRunner runner;

		private String defaultMain;

		public BeanCreator(URL[] urls) {
			functionClassLoader = new BeanCreatorClassLoader(expand(urls), getParent());
			this.defaultMain = findMain(urls);
		}

		private ClassLoader getParent() {
			ClassLoader loader = getClass().getClassLoader();
			loader = loader.getParent();
			ClassLoader parent = loader;
			while (loader.getParent() != null) {
				// If launched from a fat jar with spring.factories skip this parent level
				// (which was added by the JarLauncher).
				if (loader.getResource("META-INF/spring.factories") != null) {
					parent = loader.getParent();
				}
				loader = loader.getParent();
			}
			return parent;
		}

		private String findMain(URL[] urls) {
			for (URL url : urls) {
				try {
					File file = new File(url.toURI());
					if (file.exists()) {
						Archive archive = file.getName().endsWith(".jar")
								? new JarFileArchive(file)
								: new ExplodedArchive(file);
						String main = new ComputeLauncher(archive).getMainClass();
						if (main != null) {
							return main;
						}
					}
				}
				catch (Exception e) {
					// ignore
				}
			}
			return null;
		}

		private URL[] expand(URL[] urls) {
			List<URL> result = new ArrayList<>();
			for (URL url : urls) {
				result.addAll(expand(url));
			}
			return result.toArray(new URL[0]);
		}

		private List<URL> expand(URL url) {
			if (!"file".equals(url.getProtocol())) {
				return Collections.singletonList(url);
			}
			try {
				File file = new File(url.toURI());
				if (file.exists()) {
					Archive archive;
					if (!url.toString().endsWith(".jar")) {
						if (!new File(file, "BOOT-INF").exists()) {
							return Collections.singletonList(url);
						}
						archive = new ExplodedArchive(file);
					}
					else {
						archive = new JarFileArchive(file);
					}
					return Arrays
							.asList(new ComputeLauncher(archive).getClassLoaderUrls());
				}
				return Collections.singletonList(url);
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot create class loader for " + url,
						e);
			}
		}

		public void run(String main) {
			if (main == null) {
				main = this.defaultMain;
			}
			if (main == null) {
				return;
			}
			if (ClassUtils.isPresent(SpringApplication.class.getName(),
					functionClassLoader)) {
				logger.info("SpringApplication available. Bootstrapping: " + main);
				ClassLoader contextClassLoader = ClassUtils
						.overrideThreadContextClassLoader(functionClassLoader);
				try {
					ApplicationRunner runner = new ApplicationRunner(functionClassLoader,
							main);
					// TODO: make the runtime properties configurable
					runner.run("--spring.main.webEnvironment=false",
							"--spring.cloud.stream.enabled=false",
							"--spring.main.bannerMode=OFF",
							"--spring.main.webApplicationType=none",
							"--function.deployer.enabled=false");
					this.runner = runner;
				}
				finally {
					ClassUtils.overrideThreadContextClassLoader(contextClassLoader);
				}
			}
			else {
				throw new IllegalStateException(
						"SpringApplication not available and main class requested: "
								+ main);
			}
		}

		public String[] getFunctionNames() {
			Set<String> list = new LinkedHashSet<>();
			ClassLoader contextClassLoader = ClassUtils
					.overrideThreadContextClassLoader(functionClassLoader);
			try {
				if (this.runner.containsBean(FunctionCatalog.class.getName())) {
					Object catalog = this.runner.getBean(FunctionCatalog.class.getName());
					@SuppressWarnings("unchecked")
					Set<String> functions = (Set<String>) this.runner
							.evaluate("getNames(#type)", catalog, "type", Function.class);
					list.addAll(functions);
					@SuppressWarnings("unchecked")
					Set<String> consumers = (Set<String>) this.runner
							.evaluate("getNames(#type)", catalog, "type", Consumer.class);
					list.addAll(consumers);
					@SuppressWarnings("unchecked")
					Set<String> suppliers = (Set<String>) this.runner
							.evaluate("getNames(#type)", catalog, "type", Supplier.class);
					list.addAll(suppliers);
				}
				if (list.isEmpty()) {
					list.addAll(this.runner.getBeanNames(Function.class.getName()));
					list.addAll(this.runner.getBeanNames(Consumer.class.getName()));
					list.addAll(this.runner.getBeanNames(Supplier.class.getName()));
				}
				return list.toArray(new String[0]);
			}
			finally {
				ClassUtils.overrideThreadContextClassLoader(contextClassLoader);
			}
		}

		public Object create(String type) {
			ClassLoader contextClassLoader = ClassUtils
					.overrideThreadContextClassLoader(functionClassLoader);
			AutowireCapableBeanFactory factory = context.getAutowireCapableBeanFactory();
			try {
				Object result = null;
				if (this.runner != null) {
					result = this.runner.getBean(type);
					if (result == null) {
						if (this.runner.containsBean(FunctionCatalog.class.getName())) {
							Object catalog = this.runner
									.getBean(FunctionCatalog.class.getName());
							result = this.runner.evaluate("lookup(#function).getTarget()",
									catalog, "function", type);
							if (result != null) {
								logger.info("Located registration: " + type + " of type "
										+ result.getClass());
							}
						}
					}
					else {
						logger.info("Located bean: " + type + " of type "
								+ result.getClass());
						if (result.getClass().getName()
								.equals(FunctionRegistration.class.getName())) {
							result = this.runner.evaluate("getTarget()", result);
						}
					}
					if (result != null) {
						if (result.getClass().getName()
								.equals(FluxFunction.class.getName())) {
							result = this.runner.evaluate("getTarget()", result);
						}
					}
				}
				if (result == null) {
					logger.info("No bean found. Instantiating: " + type);
					if (ClassUtils.isPresent(type, functionClassLoader)) {
						result = factory.createBean(
								ClassUtils.resolveClassName(type, functionClassLoader));
					}
				}
				if (result != null) {
					return result;
				}
				throw new IllegalStateException("Cannot create bean for: " + type);
			}
			finally {
				ClassUtils.overrideThreadContextClassLoader(contextClassLoader);
			}
		}

		public void register(Object bean) {
			if (bean == null) {
				return;
			}
			FunctionRegistration<Object> registration = new FunctionRegistration<Object>(
					bean, FunctionProperties.functionName(counter.getAndIncrement()));
			if (this.runner != null) {
				if (this.runner.containsBean(FunctionInspector.class.getName())) {
					Object inspector = this.runner
							.getBean(FunctionInspector.class.getName());
					Class<?> input = (Class<?>) this.runner.evaluate(
							"getInputType(#function)", inspector, "function", bean);
					FunctionType type = FunctionType.from(input);
					Class<?> output = findType("getOutputType", inspector, bean);
					type = type.to(output);
					if (((Boolean) this.runner.evaluate("isMessage(#function)", inspector,
							"function", bean))) {
						type = type.message();
					}
					Class<?> inputWrapper = findType("getInputWrapper", inspector, bean);
					if (FunctionType.isWrapper(inputWrapper)) {
						type = type.wrap(inputWrapper);
					}
					Class<?> outputWrapper = findType("getOutputWrapper", inspector,
							bean);
					if (FunctionType.isWrapper(outputWrapper)) {
						type = type.wrap(outputWrapper);
					}
					registration.type(type.getType());
				}
			}
			else {
				registration.type(FunctionType.of(bean.getClass()).getType());
			}
			registration.target(bean);
			registry.register(registration);
		}

		private Class<?> findType(String method, Object inspector, Object bean) {
			return (Class<?>) this.runner.evaluate(method + "(#function)", inspector,
					"function", bean);
		}

		public void close() {
			if (this.runner != null) {
				this.runner.close();
			}
		}

	}

	private static final class BeanCreatorClassLoader extends URLClassLoader {
		private BeanCreatorClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve)
				throws ClassNotFoundException {
			try {
				return super.loadClass(name, resolve);
			}
			catch (ClassNotFoundException e) {
				if (name.contains(ContextRunner.class.getName())
						|| name.contains(PostConstruct.class.getName())) {
					// Special case for the ContextRunner. We can re-use the bytes for it,
					// and the function jar doesn't have to include them since it is only
					// used here.
					byte[] bytes;
					try {
						bytes = StreamUtils.copyToByteArray(
								getClass().getClassLoader().getResourceAsStream(
										ClassUtils.convertClassNameToResourcePath(name)
												+ ".class"));
						return defineClass(name, bytes, 0, bytes.length);
					}
					catch (IOException ex) {
						throw new ClassNotFoundException(
								"Cannot find runner class: " + name, ex);
					}
				}
				throw e;
			}
		}
	}

	@Configuration
	protected static class SingleEntryConfiguration implements BeanPostProcessor {

		@Autowired
		private Environment env;

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName)
				throws BeansException {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName)
				throws BeansException {
			String name = FunctionProperties
					.functionName(env.getProperty("function.bean", ""));
			if (bean instanceof FunctionRegistry && name.contains("|")) {
				bean = new SingleEntryFunctionRegistry((FunctionRegistry) bean, name);
			}
			return bean;
		}

	}
}
