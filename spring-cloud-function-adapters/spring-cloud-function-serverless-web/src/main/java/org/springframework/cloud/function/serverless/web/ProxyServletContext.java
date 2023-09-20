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

package org.springframework.cloud.function.serverless.web;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
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
 * Empty no-op representation of {@link ServletContext} to satisfy required dependencies to
 * successfully proxy incoming web requests to target web application.
 * Most methods are not implemented.
 *
 * @author Oleg Zhurakousky
 *
 */
public class ProxyServletContext implements ServletContext {

	private Log logger = LogFactory.getLog(ProxyServletContext.class);

	private static Enumeration<String> EMPTY_ENUM = Collections.enumeration(new ArrayList<String>());

	@Override
	public Enumeration<String> getInitParameterNames() {
		return EMPTY_ENUM;
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		return EMPTY_ENUM;
	}

	@Override
	public String getContextPath() {
		return "";
	}

	@Override
	public ServletContext getContext(String uripath) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container1");
	}

	@Override
	public int getMajorVersion() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container2");
	}

	@Override
	public int getMinorVersion() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container3");
	}

	@Override
	public int getEffectiveMajorVersion() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container4");
	}

	@Override
	public int getEffectiveMinorVersion() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container5");
	}

	@Override
	public String getMimeType(String file) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Set<String> getResourcePaths(String path) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container6");
	}

	@Override
	public URL getResource(String path) throws MalformedURLException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container7");
	}

	@Override
	public InputStream getResourceAsStream(String path) {
		return null;
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container8");
	}

	@Override
	public RequestDispatcher getNamedDispatcher(String name) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container9");
	}

	@Override
	public void log(String msg) {
		this.logger.info(msg);
	}

	@Override
	public void log(String message, Throwable throwable) {
		this.logger.error(message, throwable);
	}

	@Override
	public String getRealPath(String path) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container10");
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
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container11");
	}

	@Override
	public Object getAttribute(String name) {
		return null;
	}

	@Override
	public void setAttribute(String name, Object object) {
	}

	@Override
	public void removeAttribute(String name) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container12");
	}

	@Override
	public String getServletContextName() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container13");
	}

	@Override
	public Dynamic addServlet(String servletName, String className) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container14");
	}

	Map<String, ServletRegistration> registrations = new HashMap<>();

	@Override
	public Dynamic addServlet(String servletName, Servlet servlet) {

		ProxyServletRegistration registration = new ProxyServletRegistration(servletName, servlet, this);
		this.registrations.put(servletName, registration);
		return registration;
	}

	@Override
	public Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container15");
	}

	@Override
	public Dynamic addJspFile(String jspName, String jspFile) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container16");
	}

	@Override
	public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container17");
	}

	@Override
	public ServletRegistration getServletRegistration(String servletName) {
		return this.registrations.get(servletName);
	}

	@Override
	public Map<String, ? extends ServletRegistration> getServletRegistrations() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, String className) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container18");
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
		ProxyFilterRegistration registration = new ProxyFilterRegistration(filterName, filter);
		filterRegistrations.put(filterName, registration);
		return registration;
	}

	Map<String, FilterRegistration> filterRegistrations = new HashMap<>();

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
		try {
			Filter filter = filterClass.getDeclaredConstructor().newInstance();
			ProxyFilterRegistration registration = new ProxyFilterRegistration(filterName, filter);
			filterRegistrations.put(filterName, registration);
			return registration;
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container19");
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
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container20");
	}

	@Override
	public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container21");
	}

	@Override
	public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container22");
	}

	@Override
	public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container23");
	}

	@Override
	public void addListener(String className) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container24");
	}

	@Override
	public <T extends EventListener> void addListener(T t) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addListener(Class<? extends EventListener> listenerClass) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container25");
	}

	@Override
	public <T extends EventListener> T createListener(Class<T> c) throws ServletException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container26");
	}

	@Override
	public JspConfigDescriptor getJspConfigDescriptor() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container27");
	}

	@Override
	public ClassLoader getClassLoader() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container28");
	}

	@Override
	public void declareRoles(String... roleNames) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container29");
	}

	@Override
	public String getVirtualServerName() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container30");
	}

	@Override
	public int getSessionTimeout() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container31");
	}

	@Override
	public void setSessionTimeout(int sessionTimeout) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container32");
	}

	@Override
	public String getRequestCharacterEncoding() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container33");
	}

	@Override
	public void setRequestCharacterEncoding(String encoding) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container34");
	}

	@Override
	public String getResponseCharacterEncoding() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container35");
	}

	@Override
	public void setResponseCharacterEncoding(String encoding) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container36");
	}
}
