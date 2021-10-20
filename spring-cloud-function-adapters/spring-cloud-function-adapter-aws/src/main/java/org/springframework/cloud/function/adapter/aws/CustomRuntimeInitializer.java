/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogInitializer;
import org.springframework.cloud.function.web.source.DestinationResolver;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class CustomRuntimeInitializer implements ApplicationContextInitializer<GenericApplicationContext> {

	private static Log logger = LogFactory.getLog(CustomRuntimeInitializer.class);

	@Override
	public void initialize(GenericApplicationContext context) {
		Environment environment = context.getEnvironment();
		if (logger.isDebugEnabled()) {
			logger.debug("AWS Environment: " + System.getenv());
		}

		if (!this.isWebExportEnabled(context) && isCustomRuntime(environment)) {
			if (context.getBeanFactory().getBeanNamesForType(CustomRuntimeEventLoop.class, false, false).length == 0) {
				context.registerBean(StringUtils.uncapitalize(CustomRuntimeEventLoop.class.getSimpleName()),
						SmartLifecycle.class, () -> new CustomRuntimeEventLoop(context));
			}
		}
		else if (ContextFunctionCatalogInitializer.enabled
				&& context.getEnvironment().getProperty("spring.functional.enabled", Boolean.class, false)) {
			if (context.getBeanFactory().getBeanNamesForType(DestinationResolver.class, false, false).length == 0) {
				context.registerBean(LambdaDestinationResolver.class, () -> new LambdaDestinationResolver());
			}
		}
	}

	private boolean isCustomRuntime(Environment environment) {
		String handler = environment.getProperty("_HANDLER");
		if (StringUtils.hasText(handler)) {
			handler = handler.split(":")[0];
			logger.info("AWS Handler: " + handler);
			try {
				Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(handler);
				if (FunctionInvoker.class.isAssignableFrom(clazz) || AbstractSpringFunctionAdapterInitializer.class.isAssignableFrom(clazz)) {
					return false;
				}
			}
			catch (Exception e) {
				logger.debug("Will execute Lambda in Custom Runtime");
				return true;
			}
		}
		return false;
	}


	private boolean isWebExportEnabled(GenericApplicationContext context) {
		Boolean enabled = context.getEnvironment()
				.getProperty("spring.cloud.function.web.export.enabled", Boolean.class);
		return enabled != null && enabled;
	}

}
