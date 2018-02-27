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
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
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

	@Override
	public FunctionRegistration<?> getRegistration(Object function) {
		String name = getName(function);
		if (name == null) {
			return null;
		}
		return new FunctionRegistration<>(function).name(name)
				.type(findType(function).getType());
	}

	private FunctionType findType(Object function) {
		FunctionType type = FunctionType.from(getInputType(function))
				.to(getOutputType(function)).wrap(getInputWrapper(function));
		if (isMessage(function)) {
			type = type.message();
		}
		return type;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T lookup(Class<?> type, String name) {
		return (T) lookup(type, name, "lookup");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> getNames(Class<?> type) {
		return (Set<String>) getNames("getNames", type);
	}

	@Override
	public boolean isMessage(Object function) {
		return (Boolean) type(function, "isMessage");
	}

	@Override
	public Class<?> getInputType(Object function) {
		return (Class<?>) type(function, "getInputType");
	}

	@Override
	public Class<?> getOutputType(Object function) {
		return (Class<?>) type(function, "getOutputType");
	}

	@Override
	public Class<?> getInputWrapper(Object function) {
		return (Class<?>) type(function, "getInputWrapper");
	}

	@Override
	public Class<?> getOutputWrapper(Object function) {
		return (Class<?>) type(function, "getOutputWrapper");
	}

	@SuppressWarnings("unchecked")
	@Override
	public String getName(Object function) {
		return ((Set<String>) inspect(function, "getNames")).iterator().next();
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
		return (Set<String>) invoke(id, FunctionCatalog.class, "getNames",
				Supplier.class);
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
		return invoke(FunctionInspector.class, "getRegistration", (id, result) -> {
			return prefix(id, invoke(result, method));
		}, arg);
	}

	private Object type(Object arg, String method) {
		if (logger.isDebugEnabled()) {
			logger.debug("Inspecting " + method);
		}
		return invoke(invoke(invoke(FunctionInspector.class, "getRegistration", arg),
				"getType"), method);
	}

	private Object prefix(String id, Object result) {
		String name = this.ids.get(id);
		String prefix = name + "/";
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
		return null;
	}

	private Object lookup(Class<?> type, String name, String method) {
		if (logger.isDebugEnabled()) {
			logger.debug("Looking up " + type + " named " + name + " with " + method);
		}
		return invoke(FunctionCatalog.class, method, type, name);
	}

	private Object getNames(String method, Class<?> type) {
		if (logger.isDebugEnabled()) {
			logger.debug("Calling " + method);
		}
		return invoke(FunctionCatalog.class, method, type);
	}

	private Object invoke(Class<?> type, String method, Object... arg) {
		return invoke(type, method, null, arg);
	}

	private Object invoke(Class<?> type, String method, Callback callback,
			Object... arg) {
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
				if (callback != null) {
					return callback.call(id, result);
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
		return "lookup".equals(method) ? null : results;
	}

	private Object invoke(String id, Class<?> type, String method, Object... arg) {
		Object catalog = this.deployer.getBean(id, type);
		if (catalog == null) {
			return null;
		}
		String name = this.ids.get(id);
		String prefix = name + "/";
		if (arg.length == 2 && arg[0] instanceof Class) {
			if (arg[1] instanceof String) {
				String specific = arg[1].toString();
				if (specific.startsWith(prefix)) {
					arg[1] = specific.substring(prefix.length());
				}
				else {
					return null;
				}
			}
		}
		try {
			Object result = invoke(catalog, method, arg);
			return prefix(id, result);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot extract catalog", e);
		}
	}

	private Object invoke(Object target, String method, Object... arg) {
		MethodInvoker invoker = new MethodInvoker();
		invoker.setTargetObject(target);
		invoker.setTargetMethod(method);
		invoker.setArguments(arg);
		try {
			invoker.prepare();
			return invoker.invoke();
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot invoke method", e);
		}
	}

	public Map<String, Object> deployed() {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String name : this.names.keySet()) {
			String id = this.names.get(name);
			result.put(name, new DeployedArtifact(name, id, this.deployed.get(id)));
		}
		return result;
	}

	interface Callback {
		Object call(String id, Object result);
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
