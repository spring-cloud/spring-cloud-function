/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.function.context.FunctionProperties;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 *
 */
@ConfigurationProperties(prefix = FunctionProperties.PREFIX + ".grpc")
public class FunctionGrpcProperties {

	private final static String GRPC_PREFIX = FunctionProperties.PREFIX + ".grpc";
	/**
	 * The name of function definition property.
	 */
	public final static String SERVICE_CLASS_NAME = GRPC_PREFIX + ".service-class-name";

	/**
	 * Default gRPC port.
	 */
	public final static int GRPC_PORT = 6048;

	/**
	 * gRPC port server will bind to. Default 6048;
	 */
	private int port = GRPC_PORT;

	/**
	 * The fully qualified name of the service you wish to enable/expose.
	 * Setting this property ensures that only a single service is enabled/exposed,
	 * regardless of how many services are available on the classpath.
	 */
	private String serviceClassName;

	/**
	 * Grpc Server port.
	 */
	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}


	public String getServiceClassName() {
		return serviceClassName;
	}


	public void setServiceClassName(String serviceClassName) {
		this.serviceClassName = serviceClassName;
	}
}
