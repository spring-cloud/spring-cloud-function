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

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.thin.ThinJarAppDeployer;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.util.MethodInvoker;

public class FunctionExtractingFunctionCatalog implements FunctionCatalog {

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
		return (Consumer<T>) find(name, "lookupConsumer");
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T, R> Function<T, R> lookupFunction(String name) {
		return (Function<T, R>) find(name, "lookupFunction");
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Supplier<T> lookupSupplier(String name) {
		return (Supplier<T>) find(name, "lookupSupplier");
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

	private Object find(String name, String method) {
		for (String id : deployed) {
			Object catalog = deployer.getBean(id, FunctionCatalog.class);
			if (catalog == null) {
				continue;
			}
			try {
				MethodInvoker invoker = new MethodInvoker();
				invoker.setTargetObject(catalog);
				invoker.setTargetMethod(method);
				invoker.setArguments(new Object[] { name });
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
