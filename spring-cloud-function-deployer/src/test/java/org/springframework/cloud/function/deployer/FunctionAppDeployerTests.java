/*
 * Copyright 2012-2015 the original author or authors.
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
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.springframework.boot.loader.thin.ArchiveUtils;
import org.springframework.boot.loader.tools.LogbackInitializer;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.thin.ThinJarAppDeployer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Mark Fisher
 */
@RunWith(Parameterized.class)
public class FunctionAppDeployerTests {

	static {
		LogbackInitializer.initialize();
	}

	private static ThinJarAppDeployer deployer = new ThinJarAppDeployer();

	@BeforeClass
	public static void skip() {
		try {
			ArchiveUtils.getArchiveRoot(ArchiveUtils
					.getArchive("maven://io.spring.sample:function-sample:1.0.0.M5"));
		}
		catch (Exception e) {
			Assume.assumeNoException(
					"Could not locate jar for tests. Please build spring-cloud-function locally first.",
					e);
		}
	}

	@Parameterized.Parameters
	public static List<Object[]> data() {
		// Repeat a couple of times to ensure it's consistent
		return Arrays.asList(new Object[2][0]);
	}

	@Test
	public void directory() throws Exception {
		String first = deploy("file:../spring-cloud-function-samples/function-sample/target/classes", "",
				"--spring.cloud.function.stream.supplier.enabled=false");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(first).getState()).isEqualTo(DeploymentState.deployed);
		deployer.undeploy(first);
	}

	@Test
	public void web() throws Exception {
		String first = deploy("maven://io.spring.sample:function-sample:1.0.0.M5", "",
				"--spring.cloud.function.stream.supplier.enabled=false");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(first).getState()).isEqualTo(DeploymentState.deployed);
		deployer.undeploy(first);
	}

	@Test
	public void stream() throws Exception {
		String first = deploy("maven://io.spring.sample:function-sample:1.0.0.M5",
				"spring.cloud.deployer.thin.profile=rabbit",
				"--spring.cloud.function.stream.supplier.enabled=false", "--debug=true");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(first).getState()).isEqualTo(DeploymentState.deployed);
		deployer.undeploy(first);
	}

	private String deploy(String jarName, String properties, String... args)
			throws Exception {
		Resource resource = new FileSystemResource(
				ArchiveUtils.getArchiveRoot(ArchiveUtils.getArchive(jarName)));
		AppDefinition definition = new AppDefinition(resource.getFilename(),
				Collections.emptyMap());
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				properties(properties), Arrays.asList(args));
		String deployed = deployer.deploy(request);
		return deployed;
	}

	private Map<String, String> properties(String properties) {
		Map<String, String> map = new LinkedHashMap<>();
		Properties props = StringUtils.splitArrayElementsIntoProperties(
				StringUtils.commaDelimitedListToStringArray(properties), "=");
		if (props != null) {
			for (Object name : props.keySet()) {
				String key = (String) name;
				map.put(key, props.getProperty(key));
			}
		}
		return map;
	}

}
