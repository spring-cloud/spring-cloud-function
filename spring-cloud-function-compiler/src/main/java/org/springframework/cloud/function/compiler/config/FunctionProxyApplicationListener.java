/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.compiler.config;

import java.util.Map;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.bind.PropertySourcesBinder;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 */
public class FunctionProxyApplicationListener implements ApplicationListener<ApplicationPreparedEvent> {

	private final SupplierCompiler<?> supplierCompiler = new SupplierCompiler<>();

	private final FunctionCompiler<?, ?> functionCompiler = new FunctionCompiler<>();

	private final ConsumerCompiler<?> consumerCompiler = new ConsumerCompiler<>();

	@Override
	public void onApplicationEvent(ApplicationPreparedEvent event) {
		ConfigurableApplicationContext context = event.getApplicationContext();
		DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
		PropertySourcesBinder binder = new PropertySourcesBinder(context.getEnvironment());
		Map<String, Object> toCompile = binder.extractAll("spring.cloud.function.compile");
		for (Map.Entry<String, Object> entry : toCompile.entrySet()) {
			String name = entry.getKey();
			@SuppressWarnings("unchecked")
			Map<String, String> properties = (Map<String, String>) entry.getValue();
			String type = (properties.get("type") != null) ? properties.get("type") : "function";
			String lambda = properties.get("lambda");
			Assert.notNull(lambda,
					String.format("The 'lambda' property is required for compiling Function: %s", name));
			String inputType = properties.get("inputType");
			String outputType = properties.get("outputType");
			registerLambdaCompilingProxy(name, type, inputType, outputType, lambda, beanFactory);
		}
		Map<String, Object> toImport = binder.extractAll("spring.cloud.function.import");
		for (Map.Entry<String, Object> entry : toImport.entrySet()) {
			String name = entry.getKey();
			@SuppressWarnings("unchecked")
			Map<String, String> properties = (Map<String, String>) entry.getValue();
			String type = (properties.get("type") != null) ? properties.get("type") : "function";
			String location = properties.get("location");
			Assert.notNull(location,
					String.format("The 'location' property is required for importing Function: %s", name));
			registerByteCodeLoadingProxy(name, type, context.getResource(location), beanFactory);
		}
	}

	private void registerByteCodeLoadingProxy(String name, String type, Resource resource, DefaultListableBeanFactory beanFactory) {
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

	private void registerLambdaCompilingProxy(String name, String type, String inputType, String outputType, String lambda, DefaultListableBeanFactory beanFactory) {
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
		}
		else {
			proxyClass = LambdaCompilingFunction.class;
			args.addGenericArgumentValue(this.functionCompiler);
			if ((inputType == null && outputType != null) || (outputType == null && inputType != null)) {
				throw new IllegalArgumentException("if either input or output type is set, the other is also required");
			}
			if (inputType != null) {
				props.add("typeParameterizations", new String[] { inputType, outputType });
			}
		}
		RootBeanDefinition beanDefinition = new RootBeanDefinition(proxyClass);
		beanDefinition.setConstructorArgumentValues(args);
		beanDefinition.setPropertyValues(props);
		beanFactory.registerBeanDefinition(name, beanDefinition);
	}
}
