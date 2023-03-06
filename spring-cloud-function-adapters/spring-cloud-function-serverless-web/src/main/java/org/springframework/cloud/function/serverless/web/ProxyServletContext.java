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
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Set<String> getResourcePaths(String path) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public URL getResource(String path) throws MalformedURLException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public InputStream getResourceAsStream(String path) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
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
	public Servlet getServlet(String name) throws ServletException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Enumeration<Servlet> getServlets() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Enumeration<String> getServletNames() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public void log(String msg) {
		this.logger.info(msg);
	}

	@Override
	public void log(Exception exception, String msg) {
		this.logger.error(msg, exception);
	}

	@Override
	public void log(String message, Throwable throwable) {
		this.logger.error(message, throwable);
	}

	@Override
	public String getRealPath(String path) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public String getServerInfo() {
		return "serverless-web-proxy";
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
		return null;
	}

	@Override
	public void setAttribute(String name, Object object) {
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

	@Override
	public Dynamic addServlet(String servletName, Servlet servlet) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
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
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Map<String, ? extends ServletRegistration> getServletRegistrations() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, String className) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public FilterRegistration getFilterRegistration(String filterName) {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
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
	public Set<javax.servlet.SessionTrackingMode> getDefaultSessionTrackingModes() {
		throw new UnsupportedOperationException("This ServletContext does not represent a running web container");
	}

	@Override
	public Set<javax.servlet.SessionTrackingMode> getEffectiveSessionTrackingModes() {
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
