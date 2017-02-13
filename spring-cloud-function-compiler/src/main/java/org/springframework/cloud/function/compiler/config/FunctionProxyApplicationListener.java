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

import java.util.Map;

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
		Map<String, Object> extracted = binder.extractAll("spring.cloud.function.proxy");
		for (Map.Entry<String, Object> entry : extracted.entrySet()) {
			String name = entry.getKey();
			@SuppressWarnings("unchecked")
			Map<String, String> properties = (Map<String, String>) entry.getValue();
			String type = (properties.get("type") != null) ? properties.get("type") : "function";
			String bytecodeResource = properties.get("bytecode");
			String lambda = properties.get("lambda");
			if (!(null == bytecodeResource ^ null == lambda)) {
				throw new IllegalArgumentException("Exactly one of 'bytecode' or 'lambda' is required for a Function proxy");
			}
			if (bytecodeResource != null) {
				registerByteCodeLoadingProxy(name, type, context.getResource(bytecodeResource), beanFactory);
			}
			else {
				registerLambdaCompilingProxy(name, type, lambda, beanFactory);
			}
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

	private void registerLambdaCompilingProxy(String name, String type, String lambda, DefaultListableBeanFactory beanFactory) {
		Resource resource = new ByteArrayResource(lambda.getBytes());
		ConstructorArgumentValues args = new ConstructorArgumentValues();
		args.addGenericArgumentValue(resource);
		Class<?> proxyClass = null;
		if ("supplier".equals(type.toLowerCase())) {
			proxyClass = LambdaCompilingSupplier.class;
			args.addGenericArgumentValue(this.supplierCompiler);
		}
		else if ("consumer".equals(type.toLowerCase())) {
			proxyClass = LambdaCompilingConsumer.class;
			args.addGenericArgumentValue(this.consumerCompiler);
		}
		else {
			proxyClass = LambdaCompilingFunction.class;
			args.addGenericArgumentValue(this.functionCompiler);
		}
		RootBeanDefinition beanDefinition = new RootBeanDefinition(proxyClass);
		beanDefinition.setConstructorArgumentValues(args);
		beanFactory.registerBeanDefinition(name, beanDefinition);
	}
}
