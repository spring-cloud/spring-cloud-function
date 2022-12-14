/*
 * Copyright 2021-2022 the original author or authors.
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

package org.springframework.cloud.function.adapter.azure;

import java.util.Map;

import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.cloud.function.utils.FunctionClassUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * The instance factory used by the Spring framework to initialize Azure function instance. It is waived with the Azure
 * Java Worker through the META-INFO/services/com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector service
 * hook. The Azure Java Worker delegates scans the classpath for service definition and delegates the function class
 * creation to this instance factory.
 * @author Christian Tzolov
 * @since 3.2.9
 */
public class AzureFunctionInstanceInjector implements FunctionInstanceInjector {

	private static Log logger = LogFactory.getLog(AzureFunctionInstanceInjector.class);

	private static ConfigurableApplicationContext APPLICATION_CONTEXT;

	/**
	 * This method is called by the Azure Java Worker on every function invocation. The Worker sends in the classes
	 * annotated with @FunctionName annotations and allows the Spring framework to initialize the function instance as a
	 * Spring Bean and return the instance. Then the Worker uses the created instance to invoke the function.
	 * @param functionClass the class that contains customer Azure functions (e.g. @FunctionName annotated )
	 * @param <T> customer Azure functions class type
	 * @return the instance that will be invoked on by azure functions java worker
	 * @throws Exception any exception that is thrown by the Spring framework during instance creation
	 */
	@Override
	public <T> T getInstance(Class<T> functionClass) throws Exception {
		try {
			// Backward compatibility workaround. If the function class is of type FunctionInvoker then create plain
			// Java instance and delegate to FunctionInvoker adaptor approach.
			if (ClassUtils.isAssignable(FunctionInvoker.class, functionClass)) {
				return functionClass.newInstance();
			}

			initialize(FunctionClassUtils.getStartClass());
			Map<String, T> azureFunctionBean = APPLICATION_CONTEXT.getBeansOfType(functionClass);
			if (CollectionUtils.isEmpty(azureFunctionBean)) {
				throw new IllegalStateException(
						"Failed to retrieve Bean instance for: " + functionClass
								+ ". The class should be annotated with @Component to let the Spring framework initialize it!");
			}
			return azureFunctionBean.entrySet().iterator().next().getValue();
		}
		catch (Exception e) {
			if (APPLICATION_CONTEXT != null) {
				APPLICATION_CONTEXT.close();
			}
			throw new IllegalStateException("Failed to initialize", e);
		}
	}

	/**
	 * Create a static Application Context instance shared between multiple function invocations.
	 */
	private static void initialize(Class<?> springConfigurationClass) {
		synchronized (AzureFunctionInstanceInjector.class.getName()) {
			if (APPLICATION_CONTEXT == null) {
				logger.info("Initializing: " + springConfigurationClass);
				APPLICATION_CONTEXT = springApplication(springConfigurationClass).run();
			}
		}
	}

	private static SpringApplication springApplication(Class<?> configurationClass) {
		SpringApplication application = new org.springframework.cloud.function.context.FunctionalSpringApplication(
				configurationClass);
		application.setWebApplicationType(WebApplicationType.NONE);
		return application;
	}
}
