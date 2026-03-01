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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRegistration.Dynamic;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.ClassUtils;

/**
 * Stub representation of {@link ServletContext} to satisfy required dependencies to
 * successfully proxy incoming web requests directly (serverlessely) to target web application.
 * Most methods are not implemented.
 *
 * @author Oleg Zhurakousky
 *
 */
public class ServerlessServletContext implements ServletContext {

	private static final Log LOGGER = LogFactory.getLog(ServerlessServletContext.class);

	private HashMap<String, Object> attributes = new HashMap<>();

	private Map<String, FilterRegistration> filterRegistrations = new HashMap<>();

	private static Enumeration<String> EMPTY_ENUM = Collections.enumeration(new ArrayList<String>());

	@Override
	public Enumeration<String> getInitParameterNames() {
		return EMPTY_ENUM;
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		return Collections.enumeration(this.attributes.keySet());
	}

	@Override
	public String getContextPath() {
		return "";
	}

	@Override
	public ServletContext getContext(String uripath) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public int getMajorVersion() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public int getMinorVersion() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public int getEffectiveMajorVersion() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public int getEffectiveMinorVersion() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public String getMimeType(String file) {
		String mimeType = null;
		try {
			mimeType = Files.probeContentType(Paths.get(file));
		}
		catch (IOException | InvalidPathException e) {
			log("unable to probe for content type " + file, e);
		}
		return mimeType;
	}

	@Override
	public Set<String> getResourcePaths(String path) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public URL getResource(String path) throws MalformedURLException {
		return ServerlessServletContext.class.getResource(path);
	}

	@Override
	public InputStream getResourceAsStream(String path) {
		return ServerlessServletContext.class.getResourceAsStream(path);
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public RequestDispatcher getNamedDispatcher(String name) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public void log(String msg) {
		this.LOGGER.info(msg);
	}

	@Override
	public void log(String message, Throwable throwable) {
		this.LOGGER.error(message, throwable);
	}

	@Override
	public String getRealPath(String path) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public String getServerInfo() {
		return ClassUtils.isPresent("com.amazonaws.serverless.proxy.spring.SpringLambdaContainerHandler", ClassUtils.getDefaultClassLoader())
				? "aws-serverless-java-container/6.0"
						: "serverless-web-proxy";
	}

	@Override
	public String getInitParameter(String name) {
		return null;

	}

	@Override
	public boolean setInitParameter(String name, String value) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Object getAttribute(String name) {
		return this.attributes.get(name);
	}

	@Override
	public void setAttribute(String name, Object object) {
		this.attributes.put(name, object);
	}

	@Override
	public void removeAttribute(String name) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public String getServletContextName() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Dynamic addServlet(String servletName, String className) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	Map<String, ServletRegistration> registrations = new HashMap<>();

	@Override
	public Dynamic addServlet(String servletName, Servlet servlet) {

		ServerlessServletRegistration registration = new ServerlessServletRegistration(servletName, servlet, this);
		this.registrations.put(servletName, registration);
		return registration;
	}

	@Override
	public Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Dynamic addJspFile(String jspName, String jspFile) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public ServletRegistration getServletRegistration(String servletName) {
		return this.registrations.get(servletName);
	}

	@Override
	public Map<String, ? extends ServletRegistration> getServletRegistrations() {
		return this.registrations;
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, String className) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
		ServerlessFilterRegistration registration = new ServerlessFilterRegistration(filterName, filter);
		filterRegistrations.put(filterName, registration);
		return registration;
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
		try {
			Filter filter = filterClass.getDeclaredConstructor().newInstance();
			ServerlessFilterRegistration registration = new ServerlessFilterRegistration(filterName, filter);
			filterRegistrations.put(filterName, registration);
			return registration;
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public FilterRegistration getFilterRegistration(String filterName) {
		return this.filterRegistrations.get(filterName);
	}

	@Override
	public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
		return this.filterRegistrations;
	}

	@Override
	public SessionCookieConfig getSessionCookieConfig() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public void addListener(String className) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public <T extends EventListener> void addListener(T t) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addListener(Class<? extends EventListener> listenerClass) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public <T extends EventListener> T createListener(Class<T> c) throws ServletException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public JspConfigDescriptor getJspConfigDescriptor() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public ClassLoader getClassLoader() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public void declareRoles(String... roleNames) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public String getVirtualServerName() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public int getSessionTimeout() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public void setSessionTimeout(int sessionTimeout) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public String getRequestCharacterEncoding() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public void setRequestCharacterEncoding(String encoding) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public String getResponseCharacterEncoding() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public void setResponseCharacterEncoding(String encoding) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}
}
