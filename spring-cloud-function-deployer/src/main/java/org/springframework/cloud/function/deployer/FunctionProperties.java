/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.deployer;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.util.StringUtils;

/**
 * Configuration properties for deciding how to locate the functional class to execute.
 *
 * @author Eric Bottard
 */
public class FunctionProperties {

	/**
	 * Location(s) of jar archives containing the supplier/function/consumer class to run.
	 */
	private String[] location = new String[0];

	/**
	 * The bean name or fully qualified class name of the supplier/function/consumer to
	 * run.
	 */
	private String[] bean = new String[0];

	/**
	 * Optional main class from which to build a Spring application context
	 */
	private String main;

	public String getName() {
		return functionName(StringUtils.arrayToDelimitedString(bean, ","));
	}

	public String[] getBean() {
		return bean;
	}

	public void setBean(String[] bean) {
		this.bean = bean;
	}

	public String[] getLocation() {
		return location;
	}

	public void setLocation(String[] location) {
		this.location = location;
	}

	public String getMain() {
		return main;
	}

	public void setMain(String main) {
		this.main = main;
	}

	public static String functionName(String name) {
		if (!name.contains(",")) {
			return "function0";
		}
		List<String> names = new ArrayList<>();
		for (int i = 0; i <= StringUtils.countOccurrencesOf(name, ","); i++) {
			names.add("function" + i);
		}
		return StringUtils.collectionToDelimitedString(names, "|");
	}

	public static String functionName(int value) {
		return "function" + value;
	}

	@PostConstruct
	public void init() {
		if (location.length == 0) {
			throw new IllegalStateException(
					"No archive location provided, please configure function.location as a jar or directory.");
		}
	}
}
