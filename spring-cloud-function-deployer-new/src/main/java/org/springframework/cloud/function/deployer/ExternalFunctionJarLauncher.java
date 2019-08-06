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

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.HashMap;
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
import org.springframework.context.ApplicationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeLocator;
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
class ExternalFunctionJarLauncher extends JarLauncher {

	private static Log logger = LogFactory.getLog(ExternalFunctionJarLauncher.class);

	private final StandardEvaluationContext evalContext = new StandardEvaluationContext();

	private final Archive archive;

	ExternalFunctionJarLauncher(Archive archive) {
		super(archive);
		this.archive = archive;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void deploy(ApplicationContext deployerContext, String[] args) {

		ClassLoader currentLoader = Thread.currentThread().getContextClassLoader();
		try {
			this.launch(deployerContext, args);
			Map<String, Object> functions = this.discoverFunctions();
			if (logger.isInfoEnabled()) {
				logger.info("Discovered functions: " + functions);
			}
			FunctionRegistry functionRegistry = deployerContext.getBean(FunctionRegistry.class);
			for (Entry<String, Object> entry : functions.entrySet()) {
				FunctionRegistration registration = new FunctionRegistration(entry.getValue(), entry.getKey());
				Type type = this.findType(entry.getKey());
				if (logger.isInfoEnabled()) {
					logger.info("Registering function '" + entry.getKey() + "' of type '" + type
							+ "' in FunctionRegistry.");
				}
				registration.type(type);
				functionRegistry.register(registration);
			}
			FunctionRegistration registration = this.discovereAndLoadFunctionFromClassName(deployerContext.getBean(FunctionProperties.class));
			if (registration != null) {
				functionRegistry.register(registration);
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to deploy archive " + archive, e);
		}
		finally {
			Thread.currentThread().setContextClassLoader(currentLoader);
		}
	}

	@Override
	protected ClassLoader createClassLoader(URL[] urls) throws Exception {
		String className = DeployerContextUtils.class.getName();
		String classAsPath = className.replace('.', '/') + ".class";
		byte[] fcuBytes = StreamUtils
				.copyToByteArray(DeployerContextUtils.class.getClassLoader().getResourceAsStream(classAsPath));
		/*
		 * While LaunchedURLClassLoader is completely disconnected with the current
		 * class loader, this will still allow it to see FunctionContextUtils
		 */
		return new ClassLoader(new LaunchedURLClassLoader(urls, null)) {
			boolean functionContextUtilsLoaded;

			@Override
			protected Class<?> findClass(final String name) throws ClassNotFoundException {
				if (!functionContextUtilsLoaded && className.equals(name)) {
					Class<?> fcuClass = defineClass(name, fcuBytes, 0, fcuBytes.length);
					this.functionContextUtilsLoaded = true;
					return fcuClass;
				}
				return super.findClass(name);
			}
		};
	}

	private FunctionRegistration<?> discovereAndLoadFunctionFromClassName(FunctionProperties functionProperties) throws Exception {
		FunctionRegistration<?> functionRegistration = null;
		AtomicReference<Type> typeRef = new AtomicReference<>();
		if (StringUtils.hasText(functionProperties.getFunctionClass())) {
			Class<?> functionClass = Thread.currentThread().getContextClassLoader().loadClass(functionProperties.getFunctionClass());

			ReflectionUtils.doWithMethods(functionClass, new MethodCallback() {
				@Override
				public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
					typeRef.set(FunctionTypeUtils.getFunctionTypeFromFunctionMethod(method));
				}
			}, new MethodFilter() {
				@Override
				public boolean matches(Method method) {
					String name = method.getName();
					return typeRef.get() == null && ("apply".equals(name) || "accept".equals(name) || "get".equals(name));
				}
			});

			if (typeRef.get() != null) {
				Object functionInstance = functionClass.newInstance();

				functionRegistration = new FunctionRegistration<>(functionInstance,
						StringUtils.uncapitalize(functionClass.getSimpleName()));
				functionRegistration.type(typeRef.get());
			}
		}
		return functionRegistration;
	}

	private void launch(ApplicationContext deployerContext, String[] args) throws Exception {
		JarFile.registerUrlProtocolHandler();
		Thread.currentThread().setContextClassLoader(createClassLoader(getClassPathArchives()));
		evalContext.setTypeLocator(new StandardTypeLocator(Thread.currentThread().getContextClassLoader()));

		String mainClassName = getMainClass();
		Class<?> mainClass = Thread.currentThread().getContextClassLoader().loadClass(mainClassName);

		Class<?> bootAppClass = Thread.currentThread().getContextClassLoader()
				.loadClass(SpringApplication.class.getName());
		Method runMethod = bootAppClass.getDeclaredMethod("run", Class.class, String[].class);
		Object applicationContext = runMethod.invoke(null, mainClass, (Object) args);
		if (logger.isInfoEnabled()) {
			logger.info("Application context for archive '" + archive.getUrl() + "' is created.");
		}
		evalContext.setVariable("context", applicationContext);
		setBeanFactory(applicationContext);
	}

	private void setBeanFactory(Object applicationContext) throws Exception {
		Expression parsed = new SpelExpressionParser().parseExpression("#context.getBeanFactory()");
		Object beanFactory = parsed.getValue(evalContext);
		evalContext.setVariable("bf", beanFactory);
	}

	private Type findType(String name) {
		evalContext.setVariable("functionName", name);
		String expr = "T(" + DeployerContextUtils.class.getName() + ").findType(#bf, #functionName)";
		Expression parsed = new SpelExpressionParser().parseExpression(expr);
		Object type = parsed.getValue(evalContext);
		return (Type) type;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> discoverFunctions() throws Exception {
		Map<String, Object> allFunctions = new HashMap<String, Object>();
		Expression parsed = new SpelExpressionParser()
				.parseExpression("#context.getBeansOfType(T(java.util.function.Function))");
		allFunctions.putAll((Map<String, Object>) parsed.getValue(evalContext));
		parsed = new SpelExpressionParser().parseExpression("#context.getBeansOfType(T(java.util.function.Supplier))");
		allFunctions.putAll((Map<String, Object>) parsed.getValue(evalContext));
		parsed = new SpelExpressionParser().parseExpression("#context.getBeansOfType(T(java.util.function.Consumer))");
		allFunctions.putAll((Map<String, Object>) parsed.getValue(evalContext));
		return allFunctions;
	}
}
