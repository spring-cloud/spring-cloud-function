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
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;

/**
 *
 * @author Oleg Zhurakousky
 * @since 4.x
 *
 */
public class ServerlessFilterRegistration implements FilterRegistration, FilterRegistration.Dynamic {

	public Filter getFilter() {
		return filter;
	}

	private final String name;

	private final Filter filter;

	public ServerlessFilterRegistration(String name, Filter filter) {
		this.name = name;
		this.filter = filter;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getClassName() {
		return this.filter.getClass().getName();
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
	public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
			String... servletNames) {
		// TODO Auto-generated method stub

	}

	@Override
	public Collection<String> getServletNameMappings() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
			String... urlPatterns) {
		// TODO Auto-generated method stub

	}

	@Override
	public Collection<String> getUrlPatternMappings() {
		// TODO Auto-generated method stub
		return null;
	}

}
