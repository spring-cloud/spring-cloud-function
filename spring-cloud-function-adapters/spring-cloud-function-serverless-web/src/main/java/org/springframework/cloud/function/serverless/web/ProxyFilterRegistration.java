package org.springframework.cloud.function.serverless.web;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;

public class ProxyFilterRegistration implements FilterRegistration, FilterRegistration.Dynamic {

	public Filter getFilter() {
		return filter;
	}

	private final String name;

	private final Filter filter;

	public ProxyFilterRegistration(String name, Filter filter) {
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
