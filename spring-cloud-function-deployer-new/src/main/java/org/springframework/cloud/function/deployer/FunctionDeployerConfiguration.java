/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.deployer;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.context.annotation.Bean;

/**
 *
 * @author Oleg Zhurakousky
 *
 * @since 3.0
 *
 */
@EnableAutoConfiguration
@EnableConfigurationProperties(FunctionProperties.class)
public class FunctionDeployerConfiguration {

	@Bean
	public SmartInitializingSingleton functionDeployer(FunctionProperties functionProperties,
			FunctionRegistry functionRegistry, ApplicationArguments arguments) {
		return new SmartInitializingSingleton() {
			@Override
			public void afterSingletonsInstantiated() {
				Archive archive = null;
				try {
					archive = new JarFileArchive(new File(functionProperties.getLocation()));
				}
				catch (IOException e) {
					throw new IllegalStateException("Failed to create archive: " + functionProperties.getLocation(), e);
				}
				ExternalFunctionJarLauncher launcher = new ExternalFunctionJarLauncher(archive);
				launcher.deploy(functionRegistry, functionProperties, arguments.getSourceArgs());
			}
		};
	}
}
