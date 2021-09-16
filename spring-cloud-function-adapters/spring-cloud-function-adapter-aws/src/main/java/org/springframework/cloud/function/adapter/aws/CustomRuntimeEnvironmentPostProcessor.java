/*
 * Copyright 2019-2021 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Adds default properties to the environment for running a custom runtime in AWS.
 *
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class CustomRuntimeEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String CUSTOM_RUNTIME = "spring.cloud.function.aws.custom";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment,
			SpringApplication application) {
		if (!environment.containsProperty(CUSTOM_RUNTIME)) {
			Map<String, Object> defaults = getDefaultProperties(environment);
			defaults.put(CUSTOM_RUNTIME, true);
		}
	}

	private Map<String, Object> getDefaultProperties(
			ConfigurableEnvironment environment) {
		if (environment.getPropertySources().contains("defaultProperties")) {
			MapPropertySource source = (MapPropertySource) environment
					.getPropertySources().get("defaultProperties");
			return source.getSource();
		}
		HashMap<String, Object> map = new HashMap<String, Object>();
		environment.getPropertySources()
				.addLast(new MapPropertySource("defaultProperties", map));
		return map;
	}

}
