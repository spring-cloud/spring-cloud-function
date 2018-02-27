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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.loader.thin.ArchiveUtils;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.thin.ThinJarAppDeployer;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.stream.config.SupplierInvokingMessageProducer;
import org.springframework.cloud.stream.binder.servlet.RouteRegistrar;
import org.springframework.context.support.LiveBeansView;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MethodInvoker;

public class FunctionExtractingFunctionCatalog
		implements FunctionCatalog, FunctionInspector, DisposableBean {

	private static Log logger = LogFactory
			.getLog(FunctionExtractingFunctionCatalog.class);

	private RouteRegistrar routes;

	private SupplierInvokingMessageProducer<?> producer;

	private ThinJarAppDeployer deployer;

	private Map<String, String> deployed = new LinkedHashMap<>();

	private Map<String, String> names = new LinkedHashMap<>();

	private Map<String, String> ids = new LinkedHashMap<>();

	public FunctionExtractingFunctionCatalog() {
		this("thin", "slim");
	}

	public FunctionExtractingFunctionCatalog(String name, String... profiles) {
		deployer = new ThinJarAppDeployer(name, profiles);
	}

	@Autowired
	public void setRouteRegistrar(RouteRegistrar routes) {
		this.routes = routes;
	}

	@Autowired
	public void setProducer(SupplierInvokingMessageProducer<?> producer) {
		this.producer = producer;
	}

	@Override
	public void destroy() throws Exception {
		for (String name : new HashSet<>(names.keySet())) {
			undeploy(name);
		}
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
	public boolean isMessage(Object function) {
		return (Boolean) inspect(function, "isMessage");
	}

	@Override
	public Class<?> getInputType(Object function) {
		return (Class<?>) inspect(function, "getInputType");
	}

	@Override
	public Class<?> getOutputType(Object function) {
		return (Class<?>) inspect(function, "getOutputType");
	}

	@Override
	public Class<?> getInputWrapper(Object function) {
		return (Class<?>) inspect(function, "getInputWrapper");
	}

	@Override
	public Class<?> getOutputWrapper(Object function) {
		return (Class<?>) inspect(function, "getOutputWrapper");
	}

	@Override
	public Object convert(Object function, String value) {
		return inspect(function, "convert");
	}

	@Override
	public String getName(Object function) {
		return (String) inspect(function, "getName");
	}

	public String deploy(String name, String path, String... args) {
		Resource resource = new FileSystemResource(
				ArchiveUtils.getArchiveRoot(ArchiveUtils.getArchive(path)));
		AppDefinition definition = new AppDefinition(resource.getFilename(),
				Collections.singletonMap(LiveBeansView.MBEAN_DOMAIN_PROPERTY_NAME,
						"functions." + name));
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.singletonMap(AppDeployer.GROUP_PROPERTY_KEY, "functions"),
				Arrays.asList(args));
		String id = this.deployer.deploy(request);
		this.deployed.put(id, path);
		this.names.put(name, id);
		this.ids.put(id, name);
		register(name);
		return id;
	}

	public DeployedArtifact undeploy(String name) {
		String id = this.names.get(name);
		if (id == null) {
			// TODO: Convert to 404
			throw new IllegalStateException("No such app");
		}
		unregister(name);
		this.deployer.undeploy(id);
		String path = this.deployed.remove(id);
		this.names.remove(name);
		this.ids.remove(id);
		return new DeployedArtifact(name, id, path);
	}

	private void register(String name) {
		Set<String> names = getSupplierNames(name);
		if (routes != null) {
			logger.info("Registering routes: " + names);
			routes.registerRoutes(getSupplierNames(name));
		}
		if (producer != null) {
			// Need an ApplicationEvent that we can react to in the producer?
			for (String supplier : names) {
				producer.start(supplier);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Set<String> getSupplierNames(String name) {
		String id = this.names.get(name);
		return (Set<String>) invoke(id, FunctionCatalog.class, "getSupplierNames");
	}

	private void unregister(String name) {
		Set<String> names = getSupplierNames(name);
		if (routes != null) {
			logger.info("Unregistering routes: " + names);
			routes.unregisterRoutes(names);
		}
		if (producer != null) {
			for (String supplier : names) {
				producer.stop(supplier);
			}
		}
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
		Set<Object> results = new LinkedHashSet<>();
		Object fallback = null;
		for (String id : this.deployed.keySet()) {
			Object result = invoke(id, type, method, arg);
			if (result instanceof Collection) {
				results.addAll((Collection<?>) result);
				continue;
			}
			if (result != null) {
				if (result == Object.class) {
					// Type fallback is Object
					fallback = Object.class;
					continue;
				}
				if (result instanceof Boolean && !((Boolean) result)) {
					// Boolean fallback is false
					fallback = false;
					continue;
				}
				return result;
			}
		}
		if (fallback != null) {
			return fallback;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Results: " + results);
		}
		return arg.length > 0 ? null : results;
	}

	private Object invoke(String id, Class<?> type, String method, Object... arg) {
		Object catalog = this.deployer.getBean(id, type);
		if (catalog == null) {
			return null;
		}
		String name = this.ids.get(id);
		String prefix = name + "/";
		if (arg.length == 1) {
			if (arg[0] instanceof String) {
				String specific = arg[0].toString();
				if (specific.startsWith(prefix)) {
					arg[0] = specific.substring(prefix.length());
				}
				else {
					return null;
				}
			}
		}
		try {
			MethodInvoker invoker = new MethodInvoker();
			invoker.setTargetObject(catalog);
			invoker.setTargetMethod(method);
			invoker.setArguments(arg);
			invoker.prepare();
			Object result = invoker.invoke();
			if (result != null) {
				if (result instanceof Collection) {
					Set<String> results = new LinkedHashSet<>();
					for (Object value : (Collection<?>) result) {
						results.add(prefix + value);
					}
					return results;
				}
				else if (result instanceof String) {
					if (logger.isDebugEnabled()) {
						logger.debug("Prefixed (from \" + name + \"): " + result);
					}
					return prefix + result;
				}

				else {
					if (logger.isDebugEnabled()) {
						logger.debug("Result (from " + name + "): " + result);
					}
					return result;
				}
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot extract catalog", e);
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
