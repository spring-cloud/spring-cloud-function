/*
 * Copyright 2019-2019 the original author or authors.
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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.jar.JarFile;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 *
 */
class FunctionArchiveDeployer extends JarLauncher {

	private static Log logger = LogFactory.getLog(FunctionArchiveDeployer.class);

	private final StandardEvaluationContext evalContext = new StandardEvaluationContext();

	private LaunchedURLClassLoader archiveLoader;

	FunctionArchiveDeployer(Archive archive) {
		super(archive);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	void deploy(FunctionRegistry functionRegistry, FunctionDeployerProperties functionProperties, String[] args) {
		ClassLoader currentLoader = Thread.currentThread().getContextClassLoader();

		try {
			Thread.currentThread().setContextClassLoader(createClassLoader(discoverClassPathAcrhives()));
			evalContext.setTypeLocator(new StandardTypeLocator(Thread.currentThread().getContextClassLoader()));

			if (this.isBootApplicationWithMain()) {
				this.launchFunctionArchive(args);

				Map<String, Object> functions = this.discoverBeanFunctions();
				if (logger.isInfoEnabled() && !CollectionUtils.isEmpty(functions)) {
					logger.info("Discovered functions in deployed application context: " + functions);
				}
				for (Entry<String, Object> entry : functions.entrySet()) {
					FunctionRegistration registration = new FunctionRegistration(entry.getValue(), entry.getKey());
					Type type = this.discoverFunctionType(entry.getKey());
					if (logger.isInfoEnabled()) {
						logger.info("Registering function '" + entry.getKey() + "' of type '" + type
								+ "' in FunctionRegistry.");
					}
					registration.type(type);
					functionRegistry.register(registration);
				}
			}

			String functionClassName = discoverFunctionClassName(functionProperties);
			if (!StringUtils.isEmpty(functionClassName)) {
				FunctionRegistration registration = this.discovereAndLoadFunctionFromClassName(functionClassName);
				if (registration != null) {
					functionRegistry.register(registration);
				}
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to deploy archive " + this.getArchive(), e);
		}
		finally {
			Thread.currentThread().setContextClassLoader(currentLoader);
		}
	}

	void undeploy() {
		this.stopDeployedApplicationContext();
		try {
			this.archiveLoader.close();
			logger.info("Closed archive class loader");
		}
		catch (IOException e) {
			logger.error("Failed to closed archive class loader", e);
		}
	}

	@Override
	protected ClassLoader createClassLoader(URL[] urls) throws Exception {
		String classAsPath = DeployerContextUtils.class.getName().replace('.', '/') + ".class";
		byte[] deployerContextUtilsBytes = StreamUtils
				.copyToByteArray(DeployerContextUtils.class.getClassLoader().getResourceAsStream(classAsPath));
		/*
		 * While LaunchedURLClassLoader is completely disconnected with the current
		 * class loader, this will ensure that certain classes (e.g., org.reactivestreams.* see #shouldLoadViaDeployerLoader() )
		 * are shared across two class loaders.
		 */
		final ClassLoader deployerClassLoader = getClass().getClassLoader();
		this.archiveLoader = new LaunchedURLClassLoader(urls, deployerClassLoader.getParent()) {
			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				Class<?> clazz = null;
				if (shouldLoadViaDeployerLoader(name)) {
					clazz = deployerClassLoader.loadClass(name);
				}
				else if (name.equals(DeployerContextUtils.class.getName())) {
					/*
					 * This will ensure that `DeployerContextUtils` is available to
					 * foreign class loader for cases where foreign JAR does not
					 * have SCF dependencies.
					 */
					try {
						clazz = super.loadClass(name, false);
					}
					catch (Exception e) {
						clazz = defineClass(name, deployerContextUtilsBytes, 0, deployerContextUtilsBytes.length);
					}
				}
				else {
					clazz = super.loadClass(name, false);
				}
				return clazz;
			}
		};
		return this.archiveLoader;
	}

	private boolean shouldLoadViaDeployerLoader(String name) {
		return name.startsWith("org.reactivestreams")
				|| name.startsWith("reactor.");
	}

	private String discoverFunctionClassName(FunctionDeployerProperties functionProperties) {
		try {
			return StringUtils.hasText(functionProperties.getFunctionClass())
					? functionProperties.getFunctionClass()
							: this.getArchive().getManifest().getMainAttributes().getValue("Function-Class");
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to discover function class name", e);
		}
	}

	private boolean isBootApplicationWithMain() {
		try {
			if (this.getArchive().getManifest() == null) {
				return false;
			}
			return StringUtils.hasText(this.getArchive().getManifest().getMainAttributes().getValue("Start-Class"));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private List<Archive> discoverClassPathAcrhives() throws Exception {
		List<Archive> classPathArchives = getClassPathArchives();
		if (CollectionUtils.isEmpty(classPathArchives)) {
			classPathArchives.add(this.getArchive());
		}
		return classPathArchives;
	}

	private FunctionRegistration<?> discovereAndLoadFunctionFromClassName(String functionClassName) throws Exception {
		FunctionRegistration<?> functionRegistration = null;
		AtomicReference<Type> typeRef = new AtomicReference<>();
		Class<?> functionClass = Thread.currentThread().getContextClassLoader().loadClass(functionClassName);

		ReflectionUtils.doWithMethods(functionClass, new MethodCallback() {
			@Override
			public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
				typeRef.set(FunctionTypeUtils.getFunctionTypeFromFunctionMethod(method));
			}
		}, new MethodFilter() {
			@Override
			public boolean matches(Method method) {
				String name = method.getName();
				return typeRef.get() == null && !method.isBridge()
						&& ("apply".equals(name) || "accept".equals(name) || "get".equals(name));
			}
		});

		if (typeRef.get() != null) {
			Object functionInstance = functionClass.newInstance();
			String functionName = StringUtils.uncapitalize(functionClass.getSimpleName());
			if (logger.isInfoEnabled()) {
				logger.info("Registering function class '" + functionClass + "' of type '" + typeRef.get()
					+ "' under name '" + functionName + "'.");
			}
			functionRegistration = new FunctionRegistration<>(functionInstance, functionName);
			functionRegistration.type(typeRef.get());
		}
		return functionRegistration;
	}

	private void launchFunctionArchive(String[] args) throws Exception {
		JarFile.registerUrlProtocolHandler();

		String mainClassName = getMainClass();
		Class<?> mainClass = Thread.currentThread().getContextClassLoader().loadClass(mainClassName);

		Class<?> bootAppClass = Thread.currentThread().getContextClassLoader()
				.loadClass(SpringApplication.class.getName());
		Method runMethod = bootAppClass.getDeclaredMethod("run", Class.class, String[].class);
		Object applicationContext = runMethod.invoke(null, mainClass, (Object) args);
		if (logger.isInfoEnabled()) {
			logger.info("Application context for archive '" + this.getArchive().getUrl() + "' is created.");
		}
		evalContext.setVariable("context", applicationContext);
		setBeanFactory(applicationContext);
	}

	private void setBeanFactory(Object applicationContext) {
		Expression parsed = new SpelExpressionParser().parseExpression("#context.getBeanFactory()");
		Object beanFactory = parsed.getValue(this.evalContext);
		evalContext.setVariable("bf", beanFactory);
	}

	private Type discoverFunctionType(String name) {
		evalContext.setVariable("functionName", name);
		String expr = "T(" + DeployerContextUtils.class.getName() + ").findType(#bf, #functionName)";
		Expression parsed = new SpelExpressionParser().parseExpression(expr);
		Object type = parsed.getValue(this.evalContext);
		return (Type) type;
	}

	private void stopDeployedApplicationContext() {
		if (evalContext.lookupVariable("context") != null) { // no start-class uber jars
			Expression parsed = new SpelExpressionParser().parseExpression("#context.stop()");
			parsed.getValue(this.evalContext);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> discoverBeanFunctions() {
		Map<String, Object> allFunctions = new HashMap<String, Object>();
		if (evalContext.lookupVariable("context") != null) { // no start-class uber jars
			Expression parsed = new SpelExpressionParser()
					.parseExpression("#context.getBeansOfType(T(java.util.function.Function))");
			allFunctions.putAll((Map<String, Object>) parsed.getValue(this.evalContext));
			parsed = new SpelExpressionParser().parseExpression("#context.getBeansOfType(T(java.util.function.Supplier))");
			allFunctions.putAll((Map<String, Object>) parsed.getValue(this.evalContext));
			parsed = new SpelExpressionParser().parseExpression("#context.getBeansOfType(T(java.util.function.Consumer))");
			allFunctions.putAll((Map<String, Object>) parsed.getValue(this.evalContext));
		}
		return allFunctions;
	}
}
