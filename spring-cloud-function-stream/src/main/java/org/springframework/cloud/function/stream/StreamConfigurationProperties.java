/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Mark Fisher
 */
@ConfigurationProperties(prefix = "spring.cloud.function.stream")
public class StreamConfigurationProperties {

	private String endpoint;

	/**
	 * Interval to be used for the Duration (in milliseconds) of a non-Flux producing Supplier.
	 * Default is 0, which means the Supplier will only be invoked once. 
	 */
	private long interval = 0L;

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public long getInterval() {
		return interval;
	}

	public void setInterval(long interval) {
		this.interval = interval;
	}
}
