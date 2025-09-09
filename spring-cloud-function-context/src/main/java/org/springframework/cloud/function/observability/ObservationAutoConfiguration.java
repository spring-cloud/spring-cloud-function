/*
 * Copyright 2022-present the original author or authors.
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

package org.springframework.cloud.function.observability;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.function.context.catalog.FunctionAroundWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Oleg Zhurakousky
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration.class)
public class ObservationAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(ObservationRegistry.class)
	public FunctionAroundWrapper observationFunctionAroundWrapper(ObservationRegistry registry,
		ObjectProvider<FunctionObservationConvention> functionObservationConvention) {
		return new ObservationFunctionAroundWrapper(registry,
			functionObservationConvention.getIfAvailable(() -> null));
	}
}
