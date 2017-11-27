/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.stream.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Mark Fisher
 */
@ConfigurationProperties(prefix = "spring.cloud.function.stream")
public class StreamConfigurationProperties {

	/**
	 * The default route for a message if more than one is available and no explicit route
	 * key is provided.
	 */
	private String defaultRoute;

	/**
	 * Interval to be used for the Duration (in milliseconds) of a non-Flux producing
	 * Supplier. Default is 0, which means the Supplier will only be invoked once.
	 */
	private long interval = 0L;
	
	/**
	 * Flag to say that the default bindings to "input" and "output" are to be enabled.
	 */
	private boolean defaultBindingsEnabled = true;

	public static final String ROUTE_KEY = "stream_routekey";

	public String getDefaultRoute() {
		return defaultRoute;
	}

	public void setDefaultRoute(String defaultRoute) {
		this.defaultRoute = defaultRoute;
	}

	public long getInterval() {
		return interval;
	}

	public void setInterval(long interval) {
		this.interval = interval;
	}

	public boolean isDefaultBindingsEnabled() {
		return this.defaultBindingsEnabled;
	}

	public void setDefaultBindingsEnabled(boolean defaultBindingsEnabled) {
		this.defaultBindingsEnabled = defaultBindingsEnabled;
	}
}
