/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.function.rsocket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.function.context.FunctionProperties;

/**
 * Main configuration properties for RSocket integration with spring-cloud-function.
 * The prefix for these properties is `spring.cloud.function.rscocket`.
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
@ConfigurationProperties(prefix = FunctionProperties.PREFIX + ".rsocket")
public class RSocketFunctionProperties {

	private boolean enabled;

	private String bindAddress;

	private Integer bindPort;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getBindAddress() {
		return bindAddress;
	}

	public void setBindAddress(String bindAddress) {
		this.bindAddress = bindAddress;
	}

	public Integer getBindPort() {
		return bindPort;
	}

	public void setBindPort(Integer bindPort) {
		this.bindPort = bindPort;
	}
}
