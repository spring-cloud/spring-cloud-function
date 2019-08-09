/*
 * Copyright 2019-2019 the original author or authors.
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
import java.lang.reflect.Constructor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 */
@SpringBootApplication
@EnableConfigurationProperties(FunctionProperties.class)
public class FunctionDeployerBootstrap implements ApplicationContextAware {

	public static FunctionDeployerBootstrap instance(String... args) {
		ApplicationContext context = SpringApplication.run(FunctionDeployerBootstrap.class, args);
		return context.getBean(FunctionDeployerBootstrap.class);
	}

	private ConfigurableApplicationContext applicationContext;

	@Autowired
	private FunctionProperties functionProperties;

	@Autowired
	private FunctionCatalog functionCatalog;

	@Autowired
	private FunctionInspector functionInspector;

	@SuppressWarnings("unchecked")
	public <T extends ApplicationContainer> T run(Class<?> configurationClass, String... args) {

		try {
			Archive archive = new JarFileArchive(new File(functionProperties.getLocation()));
			ExternalFunctionJarLauncher launcher = new ExternalFunctionJarLauncher(archive);
			launcher.deploy(this.applicationContext.getBean(FunctionRegistry.class), this.applicationContext.getBean(FunctionProperties.class), args);

			Constructor<? extends ApplicationContainer> applicationContainerCtr = (Constructor<? extends ApplicationContainer>) configurationClass
					.getDeclaredConstructor(FunctionCatalog.class, FunctionInspector.class, FunctionProperties.class);

			ApplicationContainer applicationContainer = applicationContainerCtr.newInstance(this.functionCatalog,
					this.functionInspector, this.functionProperties);
			return (T) applicationContainer;
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to launch archive: " + functionProperties.getLocation(), e);
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}
}
