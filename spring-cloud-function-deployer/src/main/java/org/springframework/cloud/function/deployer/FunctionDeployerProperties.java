/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.function.deployer;

import javax.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.util.Assert;

/**
 * Configuration properties for deciding how to locate the functional class to execute.
 *
 * @author Eric Bottard
 * @author Oleg Zhurakousky
 *
 * @see FunctionProperties
 */
@ConfigurationProperties(prefix = FunctionProperties.PREFIX)
public class FunctionDeployerProperties {

	/**
	 * Location of jar archive containing the supplier/function/consumer class or bean to run.
	 */
	private String location;

	/**
	 * The name of the function class to be instantiated and loaded into FunctionCatalog. The name of the
	 * function will be decapitalized simple name of this class.
	 */
	private String functionClass;

	public void setFunctionClass(String functionClass) {
		this.functionClass = functionClass;
	}

	public String getFunctionClass() {
		return this.functionClass;
	}

	public String getLocation() {
		return this.location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	@PostConstruct
	public void init() {
		Assert.notNull(this.location, "No archive location provided, please configure spring.cloud.function.location as a jar or directory.");
	}

}
