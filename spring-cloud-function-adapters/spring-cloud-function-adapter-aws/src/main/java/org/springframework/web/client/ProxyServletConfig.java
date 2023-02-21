/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.web.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

public class ProxyServletConfig implements ServletConfig {

	private final ServletContext servletContext;

	public ProxyServletConfig(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	@Override
	public String getServletName() {
		return "hello-oleg";
	}

	@Override
	public ServletContext getServletContext() {
		return this.servletContext;
	}

	@Override
	public Enumeration<String> getInitParameterNames() {
		return Collections.enumeration(new ArrayList<String>());
	}

	@Override
	public String getInitParameter(String name) {
		return null;
	}
}
