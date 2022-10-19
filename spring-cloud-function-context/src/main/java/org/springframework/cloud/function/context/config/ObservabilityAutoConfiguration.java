/*
 * Copyright 2022-2022 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.function.observability.FunctionTracingObservationHandler;
import org.springframework.cloud.function.observability.MessageHeaderPropagatorGetter;
import org.springframework.cloud.function.observability.MessageHeaderPropagatorSetter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
public class ObservabilityAutoConfiguration {

	@Bean
	@ConditionalOnBean(Tracer.class)
	FunctionTracingObservationHandler functionTracingObservationHandler(Tracer tracer, Propagator propagator) {
		return new FunctionTracingObservationHandler(tracer, propagator, new MessageHeaderPropagatorGetter(),
				new MessageHeaderPropagatorSetter());
	}
}
