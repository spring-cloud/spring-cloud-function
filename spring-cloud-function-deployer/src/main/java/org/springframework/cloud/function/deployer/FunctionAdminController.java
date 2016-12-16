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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.loader.thin.ArchiveUtils;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Dave Syer
 *
 */
@RestController
@RequestMapping("/admin")
public class FunctionAdminController implements CommandLineRunner {

	private final FunctionExtractingAppDeployer deployer;

	private Map<String, String> deployed = new LinkedHashMap<>();

	private Map<String, String> names = new LinkedHashMap<>();

	@Autowired
	public FunctionAdminController(FunctionExtractingAppDeployer deployer) {
		this.deployer = deployer;
	}

	@PostMapping(path = "/{name}")
	public Map<String, Object> push(@PathVariable String name, @RequestParam String path)
			throws Exception {
		String id = deploy(name, path);
		return Collections.singletonMap("id", id);
	}

	@DeleteMapping(path = "/{name}")
	public Map<String, Object> undeploy(@PathVariable String name,
			@RequestParam String path) throws Exception {
		String id = names.get(name);
		if (id == null) {
			throw new IllegalStateException("No such app");
		}
		deployer.undeploy(id);
		names.remove(name);
		deployed.remove(id);
		return Collections.singletonMap("id", id);
	}

	@GetMapping({ "", "/" })
	public Map<String, Object> deployed() {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String name : names.keySet()) {
			result.put(name, new DeployedArtifact(name, names.get(name),
					deployed.get(names.get(name))));
		}
		return result;
	}

	@Override
	public void run(String... args) throws Exception {
		deploy("sample", "maven://com.example:function-sample:1.0.0.BUILD-SNAPSHOT");
	}

	private String deploy(String name, String path, String... args) throws Exception {
		Resource resource = new FileSystemResource(
				ArchiveUtils.getArchiveRoot(ArchiveUtils.getArchive(path)));
		AppDefinition definition = new AppDefinition(resource.getFilename(),
				Collections.emptyMap());
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.emptyMap(), Arrays.asList(args));
		String deployed = deployer.deploy(request);
		this.deployed.put(deployed, path);
		this.names.put(deployed, name);
		return deployed;
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
