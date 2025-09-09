/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.cloud.function.cloudevent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.function.core.FunctionInvocationHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * Configuration class with components relevant to Cloud Events support.
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
@Configuration(proxyBeanMethods = false)
public class CloudEventsFunctionExtensionConfiguration {

	// The following two beans are intended to be mutually exclusive. Only one should be activated based
	// on the presence of Cloud Event SDK API
	@Bean
	@ConditionalOnMissingBean
	public FunctionInvocationHelper<Message<?>> nativeFunctionInvocationHelper(@Nullable CloudEventHeaderEnricher cloudEventHeadersProvider) {
		return new CloudEventsFunctionInvocationHelper(cloudEventHeadersProvider);
	}
}
