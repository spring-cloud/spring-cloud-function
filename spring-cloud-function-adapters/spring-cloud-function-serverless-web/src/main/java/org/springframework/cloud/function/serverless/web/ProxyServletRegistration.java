package org.springframework.cloud.function.serverless.web;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletSecurityElement;

public class ProxyServletRegistration implements ServletRegistration, ServletRegistration.Dynamic, Comparable<ProxyServletRegistration> {

	private final String servletName;

	private final Servlet servlet;

	private final ServletContext servletContext;

	private int loadOnStartup;

	public ProxyServletRegistration(String servletName, Servlet servlet, ServletContext servletContext) {
		this.servlet = servlet;
		this.servletName = servletName;
		this.servletContext = servletContext;
	}

	@Override
	public String getName() {
		return this.servletName;
	}

	@Override
	public String getClassName() {
		// TODO Auto-generated method stub
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
	public int compareTo(ProxyServletRegistration o) {
		return Integer.compare(this.loadOnStartup, o.getLoadOnStartup());
	}

}
