/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.function.serverless.web;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletSecurityElement;

/**
 * @author Oleg Zhurakousky
 * @since 4.x
 *
 */
public class ServerlessServletRegistration
		implements ServletRegistration, ServletRegistration.Dynamic, Comparable<ServerlessServletRegistration> {

	private final String servletName;

	private final Servlet servlet;

	private final ServletContext servletContext;

	private int loadOnStartup;

	public ServerlessServletRegistration(String servletName, Servlet servlet, ServletContext servletContext) {
		this.servlet = servlet;
		this.servletName = servletName;
		this.servletContext = servletContext;
	}

	@Override
	public String getName() {
		return this.servletName;
	}

	public ServletContext getServletContext() {
		return this.servletContext;
	}

	@Override
	public String getClassName() {
		if (this.servlet != null) {
			return this.servletName.getClass().getName();
		}
		return null;
	}

	@Override
	public boolean setInitParameter(String name, String value) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getInitParameter(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<String> setInitParameters(Map<String, String> initParameters) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, String> getInitParameters() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setAsyncSupported(boolean isAsyncSupported) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setLoadOnStartup(int loadOnStartup) {
		this.loadOnStartup = loadOnStartup;
	}

	public int getLoadOnStartup() {
		return this.loadOnStartup;
	}

	@Override
	public Set<String> setServletSecurity(ServletSecurityElement constraint) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMultipartConfig(MultipartConfigElement multipartConfig) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setRunAsRole(String roleName) {
		// TODO Auto-generated method stub

	}

	@Override
	public Set<String> addMapping(String... urlPatterns) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<String> getMappings() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRunAsRole() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int compareTo(ServerlessServletRegistration o) {
		return Integer.compare(this.loadOnStartup, o.getLoadOnStartup());
	}

}
