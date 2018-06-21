/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.compiler.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.properties.ConfigurationBeanFactoryMetadata;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.cloud.function.compiler.ConsumerCompiler;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.compiler.SupplierCompiler;
import org.springframework.cloud.function.compiler.proxy.ByteCodeLoadingConsumer;
import org.springframework.cloud.function.compiler.proxy.ByteCodeLoadingFunction;
import org.springframework.cloud.function.compiler.proxy.ByteCodeLoadingSupplier;
import org.springframework.cloud.function.compiler.proxy.LambdaCompilingConsumer;
import org.springframework.cloud.function.compiler.proxy.LambdaCompilingFunction;
import org.springframework.cloud.function.compiler.proxy.LambdaCompilingSupplier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 */
@ConfigurationProperties("spring.cloud.function")
public class FunctionProxyApplicationListener
		implements ApplicationListener<ApplicationPreparedEvent> {

	private final SupplierCompiler<?> supplierCompiler = new SupplierCompiler<>();

	private final FunctionCompiler<?, ?> functionCompiler = new FunctionCompiler<>();

	private final ConsumerCompiler<?> consumerCompiler = new ConsumerCompiler<>();

	/**
	 * Configuration for function bodies, which will be compiled. The key in the map is
	 * the function name and the value is a map containing a key "lambda" which is the
	 * body to compile, and optionally a "type" (defaults to "function"). Can also contain
	 * "inputType" and "outputType" in case it is ambiguous.
	 */
	private final Map<String, Object> compile = new HashMap<>();

	/**
	 * Configuration for a set of files containing function bodies, which will be imported
	 * and compiled. The key in the map is the function name and the value is another map,
	 * containing a "location" of the file to compile and (optionally) a "type" (defaults
	 * to "function").
	 */
	private final Map<String, Object> imports = new HashMap<>();

	public Map<String, Object> getCompile() {
		return compile;
	}

	public Map<String, Object> getImports() {
		return imports;
	}

	@Override
	public void onApplicationEvent(ApplicationPreparedEvent event) {
		ConfigurableApplicationContext context = event.getApplicationContext();
		DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context
				.getBeanFactory();
		bind(context);
		for (Map.Entry<String, Object> entry : compile.entrySet()) {
			String name = entry.getKey();
			@SuppressWarnings("unchecked")
			Map<String, String> properties = (Map<String, String>) entry.getValue();
			String type = (properties.get("type") != null) ? properties.get("type")
					: "function";
			String lambda = properties.get("lambda");
			Assert.notNull(lambda, String.format(
					"The 'lambda' property is required for compiling Function: %s",
					name));
			String inputType = properties.get("inputType");
			String outputType = properties.get("outputType");
			registerLambdaCompilingProxy(name, type, inputType, outputType, lambda,
					beanFactory);
		}
		for (Map.Entry<String, Object> entry : imports.entrySet()) {
			String name = entry.getKey();
			@SuppressWarnings("unchecked")
			Map<String, String> properties = (Map<String, String>) entry.getValue();
			String type = (properties.get("type") != null) ? properties.get("type")
					: "function";
			String location = properties.get("location");
			Assert.notNull(location, String.format(
					"The 'location' property is required for importing Function: %s",
					name));
			registerByteCodeLoadingProxy(name, type, context.getResource(location),
					beanFactory);
		}
	}

	private void bind(ConfigurableApplicationContext context) {
		ConfigurationPropertiesBindingPostProcessor post = new ConfigurationPropertiesBindingPostProcessor();
		maybeSetBeanFactory(context, post);
		try {
			post.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot bind properties", e);
		}
		post.postProcessBeforeInitialization(this, getClass().getName());
	}

	private void maybeSetBeanFactory(ConfigurableApplicationContext context,
			ConfigurationPropertiesBindingPostProcessor post) {
		StaticApplicationContext other = new StaticApplicationContext();
		other.setEnvironment(context.getEnvironment());
		other.registerSingleton(ConfigurationBeanFactoryMetadata.class.getName(), ConfigurationBeanFactoryMetadata.class);
		other.setParent(context);
		post.setApplicationContext(other);
	}

	private void registerByteCodeLoadingProxy(String name, String type, Resource resource,
			DefaultListableBeanFactory beanFactory) {
		Class<?> proxyClass = null;
		if ("supplier".equals(type.toLowerCase())) {
			proxyClass = ByteCodeLoadingSupplier.class;
		}
		else if ("consumer".equals(type.toLowerCase())) {
			proxyClass = ByteCodeLoadingConsumer.class;
		}
		else {
			proxyClass = ByteCodeLoadingFunction.class;
		}
		RootBeanDefinition beanDefinition = new RootBeanDefinition(proxyClass);
		ConstructorArgumentValues args = new ConstructorArgumentValues();
		args.addGenericArgumentValue(resource);
		beanDefinition.setConstructorArgumentValues(args);
		beanFactory.registerBeanDefinition(name, beanDefinition);
	}

	private void registerLambdaCompilingProxy(String name, String type, String inputType,
			String outputType, String lambda, DefaultListableBeanFactory beanFactory) {
		Resource resource = new ByteArrayResource(lambda.getBytes());
		ConstructorArgumentValues args = new ConstructorArgumentValues();
		MutablePropertyValues props = new MutablePropertyValues();
		args.addGenericArgumentValue(resource);
		Class<?> proxyClass = null;
		if ("supplier".equals(type.toLowerCase())) {
			proxyClass = LambdaCompilingSupplier.class;
			args.addGenericArgumentValue(this.supplierCompiler);
			if (outputType != null) {
				props.add("typeParameterizations", outputType);
			}
		}
		else if ("consumer".equals(type.toLowerCase())) {
			proxyClass = LambdaCompilingConsumer.class;
			args.addGenericArgumentValue(this.consumerCompiler);
			if (inputType != null) {
				props.add("typeParameterizations", inputType);
			}
		}
		else {
			proxyClass = LambdaCompilingFunction.class;
			args.addGenericArgumentValue(this.functionCompiler);
			if ((inputType == null && outputType != null)
					|| (outputType == null && inputType != null)) {
				throw new IllegalArgumentException(
						"if either input or output type is set, the other is also required");
			}
			if (inputType != null) {
				props.add("typeParameterizations",
						new String[] { inputType, outputType });
			}
		}
		RootBeanDefinition beanDefinition = new RootBeanDefinition(proxyClass);
		beanDefinition.setConstructorArgumentValues(args);
		beanDefinition.setPropertyValues(props);
		beanFactory.registerBeanDefinition(name, beanDefinition);
	}
}
