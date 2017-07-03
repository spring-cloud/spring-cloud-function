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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
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

public class FunctionExtractingFunctionCatalog
		implements FunctionCatalog, FunctionInspector {

	private static Log logger = LogFactory
			.getLog(FunctionExtractingFunctionCatalog.class);

	private ThinJarAppDeployer deployer;

	private Map<String, String> deployed = new LinkedHashMap<>();

	private Map<String, String> names = new LinkedHashMap<>();

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

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> getSupplierNames() {
		return (Set<String>) catalog("getSupplierNames");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> getFunctionNames() {
		return (Set<String>) catalog("getFunctionNames");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> getConsumerNames() {
		return (Set<String>) catalog("getConsumerNames");
	}

	@Override
	public boolean isMessage(String name) {
		return (Boolean) inspect(name, "isMessage");
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
	public Class<?> getInputWrapper(String name) {
		return (Class<?>) inspect(name, "getInputWrapper");
	}

	@Override
	public Class<?> getOutputWrapper(String name) {
		return (Class<?>) inspect(name, "getOutputWrapper");
	}

	@Override
	public Object convert(String name, String value) {
		return inspect(name, "convert");
	}

	@Override
	public String getName(Object function) {
		return (String) inspect(function, "getName");
	}

	public String deploy(String name, AppDeploymentRequest request) {
		String id = this.deployer.deploy(request);
		try {
			this.deployed.put(id, request.getResource().getURI().toString());
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot locate resource for " + name, e);
		}
		this.names.put(name, id);
		return id;
	}

	public DeployedArtifact undeploy(String name) {
		String id = this.names.get(name);
		if (id == null) {
			// TODO: Convert to 404
			throw new IllegalStateException("No such app");
		}
		this.deployer.undeploy(id);
		this.deployed.remove(id);
		this.names.remove(name);
		String path = this.deployed.remove(id);
		return new DeployedArtifact(id, name, path);
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

	private Object catalog(String method) {
		if (logger.isDebugEnabled()) {
			logger.debug("Calling " + method);
		}
		return invoke(FunctionCatalog.class, method);
	}

	private Object invoke(Class<?> type, String method, Object... arg) {
		for (String id : this.deployed.keySet()) {
			Object catalog = this.deployer.getBean(id, type);
			if (catalog == null) {
				continue;
			}
			try {
				MethodInvoker invoker = new MethodInvoker();
				invoker.setTargetObject(catalog);
				invoker.setTargetMethod(method);
				invoker.setArguments(arg);
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

	public Map<String, Object> deployed() {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String name : this.names.keySet()) {
			String id = this.names.get(name);
			result.put(name, new DeployedArtifact(name, id, this.deployed.get(id)));
		}
		return result;
	}

}

class DeployedArtifact {

	private String name;
	private String id;
	private String path;

	public DeployedArtifact() {
	}

	public DeployedArtifact(String name, String id, String path) {
		this.name = name;
		this.id = id;
		this.path = path;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

}
