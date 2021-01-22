/*
 * Copyright 2018-2019 the original author or authors.
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

import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogInitializer;
import org.springframework.cloud.function.web.source.DestinationResolver;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
@Order(0)
public class CustomRuntimeInitializer implements ApplicationContextInitializer<GenericApplicationContext> {

	private static Log logger = LogFactory.getLog(CustomRuntimeInitializer.class);

	@Override
	public void initialize(GenericApplicationContext context) {
		if (logger.isDebugEnabled()) {
			logger.debug("AWS Environment: " + System.getenv());
		}

		// the presence of AWS_LAMBDA_RUNTIME_API signifies Custom Runtime
		if (!this.isWebExportEnabled(context) && StringUtils.hasText(System.getenv("AWS_LAMBDA_RUNTIME_API"))) {
			if (context.getBeanFactory().getBeanNamesForType(CustomRuntimeEventLoop.class, false, false).length == 0) {
				context.registerBean(StringUtils.uncapitalize(CustomRuntimeEventLoop.class.getSimpleName()),
						CommandLineRunner.class, () -> args -> CustomRuntimeEventLoop.eventLoop(context));
			}
		}


//		Boolean enabled = context.getEnvironment()
//				.getProperty("spring.cloud.function.web.export.enabled", Boolean.class);
//		if (enabled == null || !enabled) {
//			if (StringUtils.hasText(System.getenv("AWS_LAMBDA_RUNTIME_API"))) {
//				if (context.getBeanFactory().getBeanNamesForType(CustomRuntimeEventLoop.class, false, false).length == 0) {
//					context.registerBean(StringUtils.uncapitalize(CustomRuntimeEventLoop.class.getSimpleName()),
//							CommandLineRunner.class, () -> args -> CustomRuntimeEventLoop.eventLoop(context));
//				}
//			}
//		}
		else if (ContextFunctionCatalogInitializer.enabled
				&& context.getEnvironment().getProperty("spring.functional.enabled", Boolean.class, false)) {
			if (context.getBeanFactory().getBeanNamesForType(DestinationResolver.class, false, false).length == 0) {
				context.registerBean(LambdaDestinationResolver.class, () -> new LambdaDestinationResolver());
			}
			context.registerBean(StringUtils.uncapitalize(CustomRuntimeAutoConfiguration.class.getSimpleName()),
					CommandLineRunner.class, () -> args -> CustomRuntimeAutoConfiguration.background());
		}
	}

	private boolean isWebExportEnabled(GenericApplicationContext context) {
		Boolean enabled = context.getEnvironment()
				.getProperty("spring.cloud.function.web.export.enabled", Boolean.class);
		return enabled != null && enabled;
	}

}
