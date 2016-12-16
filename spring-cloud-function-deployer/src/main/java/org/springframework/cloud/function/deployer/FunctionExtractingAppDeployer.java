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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.thin.ThinJarAppDeployer;
import org.springframework.cloud.deployer.thin.ThinJarAppWrapper;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.util.MethodInvoker;
import org.springframework.util.ReflectionUtils;

public class FunctionExtractingAppDeployer extends ThinJarAppDeployer
		implements FunctionCatalog {

	private static final Log logger = LogFactory
			.getLog(FunctionExtractingAppDeployer.class);

	private final Map<String, Function<?, ?>> functions = new HashMap<>();
	private final Map<String, Consumer<?>> consumers = new HashMap<>();
	private final Map<String, Supplier<?>> suppliers = new HashMap<>();

	public FunctionExtractingAppDeployer() {
		this("thin", "slim");
	}

	public FunctionExtractingAppDeployer(String name, String... profiles) {
		super(name, profiles);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Consumer<T> lookupConsumer(String name) {
		return (Consumer<T>) consumers.get(name);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T, R> Function<T, R> lookupFunction(String name) {
		return (Function<T, R>) functions.get(name);
	}

	@Override
	public <T, R> Function<T, R> composeFunction(String... functionNames) {
		Function<T, R> function = this.lookupFunction(functionNames[0]);
		for (int i = 1; i < functionNames.length; i++) {
			function = function.andThen(this.lookupFunction(functionNames[i]));
		}
		return function;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Supplier<T> lookupSupplier(String name) {
		return (Supplier<T>) suppliers.get(name);
	}

	@Override
	public String deploy(AppDeploymentRequest request) {
		String id = super.deploy(request);
		functions.putAll(functions(id));
		suppliers.putAll(suppliers(id));
		consumers.putAll(consumers(id));
		return id;
	}

	@Override
	public void undeploy(String id) {
		super.undeploy(id);
		for (String name : functions(id).keySet()) {
			functions.remove(name);
		}
		for (String name : suppliers(id).keySet()) {
			suppliers.remove(name);
		}
		for (String name : consumers(id).keySet()) {
			consumers.remove(name);
		}
	}

	private Map<String, Function<?, ?>> functions(String id) {
		Map<String, Function<?, ?>> map = new HashMap<>();
		ThinJarAppWrapper wrapper = getWrapper(id);
		if (wrapper == null) {
			return map;
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, ? extends Function<?, ?>> result = (Map<String, ? extends Function<?, ?>>) getBeans(
					wrapper, Function.class);
			map.putAll(result);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot extract functions", e);
		}
		logger.info("Loaded functions: " + map.keySet());
		return map;
	}

	private Map<String, Consumer<?>> consumers(String id) {
		Map<String, Consumer<?>> map = new HashMap<>();
		ThinJarAppWrapper wrapper = getWrapper(id);
		if (wrapper == null) {
			return map;
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, ? extends Consumer<?>> result = (Map<String, ? extends Consumer<?>>) getBeans(
					wrapper, Consumer.class);
			map.putAll(result);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot extract consumers", e);
		}
		logger.info("Loaded consumers: " + map.keySet());
		return map;
	}

	private Map<String, Supplier<?>> suppliers(String id) {
		Map<String, Supplier<?>> map = new HashMap<>();
		ThinJarAppWrapper wrapper = getWrapper(id);
		if (wrapper == null) {
			return map;
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, ? extends Supplier<?>> result = (Map<String, ? extends Supplier<?>>) getBeans(
					wrapper, Supplier.class);
			map.putAll(result);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot extract suppliers", e);
		}
		logger.info("Loaded suppliers: " + map.keySet());
		return map;
	}

	private <T> Map<String, ? extends T> getBeans(ThinJarAppWrapper wrapper,
			Class<T> type) throws IllegalAccessException, ClassNotFoundException,
			NoSuchMethodException, InvocationTargetException {
		Object app = findContext(wrapper);
		MethodInvoker invoker = new MethodInvoker();
		invoker.setTargetObject(app);
		invoker.setTargetMethod("getBeansOfType");
		invoker.setArguments(new Object[] { type });
		invoker.prepare();
		@SuppressWarnings("unchecked")
		Map<String, T> result = (Map<String, T>) invoker.invoke();
		return result;
	}

	private Object findContext(ThinJarAppWrapper wrapper) {
		Object app = wrapper.getApp();
		Field field = ReflectionUtils.findField(app.getClass(), "context");
		ReflectionUtils.makeAccessible(field);
		app = ReflectionUtils.getField(field, app);
		return app;
	}

}
