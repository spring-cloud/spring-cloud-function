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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 *
 * Configuration class which creates an instance of {@link SmartLifecycle}
 * which deploys and un-deploys packages archives via it's {@link SmartLifecycle#start()}
 * and {@link SmartLifecycle#stop()} operations.
 * <br>
 * @author Oleg Zhurakousky
 * @author Eric Bottard
 *
 * @since 3.0
 *
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FunctionProperties.class)
public class FunctionDeployerConfiguration {

	private static Log logger = LogFactory.getLog(FunctionDeployerConfiguration.class);

	@Bean
	SmartLifecycle functionArchiveDeployer(FunctionProperties functionProperties,
			FunctionRegistry functionRegistry, ApplicationArguments arguments) {

		ApplicationArguments updatedArguments = this.updateArguments(arguments);

		Archive archive = null;
		try {
			File file = new File(functionProperties.getLocation());
			if (!file.exists()) {
				throw new IllegalStateException("Failed to create archive: " + functionProperties.getLocation() + " does not exist");
			}
			else if (file.isDirectory()) {
				archive = new ExplodedArchive(file);
			}
			else {
				archive = new JarFileArchive(file);
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to create archive: " + functionProperties.getLocation(), e);
		}
		FunctionArchiveDeployer deployer = new FunctionArchiveDeployer(archive);

		return new SmartLifecycle() {

			private boolean running;

			@Override
			public void stop() {
				if (logger.isInfoEnabled()) {
					logger.info("Undeploying archive: " + functionProperties.getLocation());
				}
				deployer.undeploy();
				if (logger.isInfoEnabled()) {
					logger.info("Successfully undeployed archive: " + functionProperties.getLocation());
				}
				this.running = false;
			}

			@Override
			public void start() {
				if (logger.isInfoEnabled()) {
					logger.info("Deploying archive: " + functionProperties.getLocation());
				}
				deployer.deploy(functionRegistry, functionProperties, updatedArguments.getSourceArgs());
				this.running = true;
				if (logger.isInfoEnabled()) {
					logger.info("Successfully deployed archive: " + functionProperties.getLocation());
				}
			}

			@Override
			public boolean isRunning() {
				return this.running;
			}

			@Override
			public int getPhase() {
				return Integer.MAX_VALUE - 1000;
			}
		};

	}

	/*
	 * We need to update actual arguments to ensure that when we may be passing to the deployed archive has the right properties.
	 * For the current application FunctionProperties already set as a result of EnvironmentPostProcessor
	 */
	private ApplicationArguments updateArguments(ApplicationArguments arguments) {
		List<String> originalArguments =  new ArrayList<String>(Arrays.asList(arguments.getSourceArgs()));

		if (arguments.containsOption("function.name")) {
			originalArguments.add(FunctionProperties.PREFIX + ".function-name=" + arguments.getOptionValues("function.name").get(0));
		}
		if (arguments.containsOption("function.location")) {
			originalArguments.add(FunctionProperties.PREFIX + ".location=" + arguments.getOptionValues("function.location").get(0));
		}
		ApplicationArguments updatedArguments = new DefaultApplicationArguments(originalArguments.toArray(new String[] {}));
		return updatedArguments;
	}


	/**
	 * Instance of {@link EnvironmentPostProcessor} which ensures that legacy
	 * Function property names are still honored.
	 */
	static class LegacyPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor {
		@Override
		public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
			if (environment.containsProperty("function.name")) {
				System.setProperty(FunctionProperties.PREFIX + ".function-name", environment.getProperty("function.name"));
			}
			if (environment.containsProperty("function.location")) {
				System.setProperty(FunctionProperties.PREFIX + ".location", environment.getProperty("function.location"));
			}
		}
	}
}
