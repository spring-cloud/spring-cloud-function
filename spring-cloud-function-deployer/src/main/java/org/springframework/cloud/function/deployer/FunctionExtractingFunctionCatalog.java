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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.thin.ThinJarAppDeployer;
import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.util.MethodInvoker;

public class FunctionExtractingFunctionCatalog implements FunctionCatalog, FunctionInspector {

	private static Log logger = LogFactory
			.getLog(FunctionExtractingFunctionCatalog.class);

	private final Set<String> deployed = new HashSet<>();

	private ThinJarAppDeployer deployer;

	public FunctionExtractingFunctionCatalog() {
		this("thin", "slim");
	}

	public FunctionExtractingFunctionCatalog(String name, String... profiles) {
		deployer = new ThinJarAppDeployer(name, profiles);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Consumer<T> lookupConsumer(String name) {
		return (Consumer<T>) lookup(name, "lookupConsumer");
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T, R> Function<T, R> lookupFunction(String name) {
		return (Function<T, R>) lookup(name, "lookupFunction");
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Supplier<T> lookupSupplier(String name) {
		return (Supplier<T>) lookup(name, "lookupSupplier");
	}

	@Override
	public Class<?> getInputType(String name) {
		return (Class<?>) inspect(name, "getInputType");
	}

	@Override
	public Class<?> getOutputType(String name) {
		return (Class<?>) inspect(name, "getOutputType");
	}

	@Override
	public Object convert(String name, String value) {
		return inspect(name, "convert");
	}

	@Override
	public String getName(Object function) {
		return (String) inspect(function, "getName");
	}

	public String deploy(AppDeploymentRequest request) {
		String id = deployer.deploy(request);
		deployed.add(id);
		return id;
	}

	public void undeploy(String id) {
		deployer.undeploy(id);
		deployed.remove(id);
	}

	private Object inspect(Object arg, String method) {
		if (logger.isDebugEnabled()) {
			logger.debug("Inspecting " + method);
		}
		return invoke(FunctionInspector.class, method, arg);
	}
	
	private Object lookup(String name, String method) {
		if (logger.isDebugEnabled()) {
			logger.debug("Looking up " + name + " with " + method);
		}
		return invoke(FunctionCatalog.class, method, name);
	}
	
	private Object invoke(Class<?> type, String method, Object arg) {
		for (String id : deployed) {
			Object catalog = deployer.getBean(id, type);
			if (catalog == null) {
				continue;
			}
			try {
				MethodInvoker invoker = new MethodInvoker();
				invoker.setTargetObject(catalog);
				invoker.setTargetMethod(method);
				invoker.setArguments(new Object[] { arg });
				invoker.prepare();
				Object result = invoker.invoke();
				if (result != null) {
					return result;
				}
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot extract catalog", e);
			}
		}
		return null;
	}

}
