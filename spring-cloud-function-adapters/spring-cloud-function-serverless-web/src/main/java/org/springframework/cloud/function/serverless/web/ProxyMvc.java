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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Represents the main entry point into interaction with web application over light-weight proxy.
 * After creating an instance via {@link #INSTANCE(Class...)} operation which will initialize the provided component
 * classes of your web application (effectively starting your web application less web server),
 * you use {@link #service(HttpServletRequest, HttpServletResponse)} operation to send request and receive a response.
 *
 * @author Oleg Zhurakousky
 *
 */
public class ProxyMvc {

	static final String MVC_RESULT_ATTRIBUTE = ProxyMvc.class.getName().concat(".MVC_RESULT_ATTRIBUTE");

	private final DispatcherServlet servlet;

	private final Filter[] filters;

	private final ConfigurableWebApplicationContext applicationContext;

	public static ProxyMvc INSTANCE(Class<?>... componentClasses) {
		Assert.notEmpty(componentClasses, "'componentClasses' must not be null or empty");

		ProxyServletContext servletContext = new ProxyServletContext();
		GenericWebApplicationContext applpicationContext = new GenericWebApplicationContext(servletContext);
		AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(applpicationContext);
		reader.register(componentClasses);

		try {
			DispatcherServlet servlet = new DispatcherServlet(applpicationContext);
			servlet.init(new ProxyServletConfig(servletContext));
			applpicationContext.registerBean(DispatcherServlet.class, servlet);

			return new ProxyMvc(servlet, applpicationContext);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to create MVC Proxy", e);
		}
	}

	/**
	 * Private constructor, not for direct instantiation.
	 */
	ProxyMvc(DispatcherServlet servlet, ConfigurableWebApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
		this.servlet = servlet;
		this.filters = applicationContext.getBeansOfType(Filter.class).values().toArray(new Filter[0]);
	}

	public void stop() {
		this.applicationContext.stop();
	}

	/**
	 * Perform a request and return a type that allows chaining further actions,
	 * such as asserting expectations, on the result.
	 *
	 * @param requestBuilder used to prepare the request to execute; see static
	 *                       factory methods in
	 *                       {@link org.springframework.test.web.servlet.request.MockMvcRequestBuilders}
	 * @return an instance of {@link ResultActions} (never {@code null})
	 * @see org.springframework.test.web.servlet.request.MockMvcRequestBuilders
	 * @see org.springframework.test.web.servlet.result.MockMvcResultMatchers
	 */
	public void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
		ProxyFilterChain filterChain = new ProxyFilterChain(this.servlet, this.filters);
		filterChain.doFilter(request, response);
	}

	private static class ProxyFilterChain implements FilterChain {

		@Nullable
		private ServletRequest request;

		@Nullable
		private ServletResponse response;

		private final List<Filter> filters;

		@Nullable
		private Iterator<Filter> iterator;


		/**
		 * Create a {@code FilterChain} with Filter's and a Servlet.
		 *
		 * @param servlet the {@link Servlet} to invoke in this {@link FilterChain}
		 * @param filters the {@link Filter}'s to invoke in this {@link FilterChain}
		 * @since 3.2
		 */
		ProxyFilterChain(Servlet servlet, Filter... filters) {
			Assert.notNull(filters, "filters cannot be null");
			Assert.noNullElements(filters, "filters cannot contain null values");
			this.filters = initFilterList(servlet, filters);
		}

		private static List<Filter> initFilterList(Servlet servlet, Filter... filters) {
			Filter[] allFilters = ObjectUtils.addObjectToArray(filters, new ServletFilterProxy(servlet));
			return Arrays.asList(allFilters);
		}

		/**
		 * Return the request that {@link #doFilter} has been called with.
		 */
		@Nullable
		public ServletRequest getRequest() {
			return this.request;
		}

		/**
		 * Return the response that {@link #doFilter} has been called with.
		 */
		@Nullable
		public ServletResponse getResponse() {
			return this.response;
		}

		/**
		 * Invoke registered {@link Filter Filters} and/or {@link Servlet} also saving
		 * the request and response.
		 */
		@Override
		public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
			Assert.notNull(request, "Request must not be null");
			Assert.notNull(response, "Response must not be null");
			Assert.state(this.request == null, "This FilterChain has already been called!");

			if (this.iterator == null) {
				this.iterator = this.filters.iterator();
			}

			if (this.iterator.hasNext()) {
				Filter nextFilter = this.iterator.next();
				nextFilter.doFilter(request, response, this);
			}

			this.request = request;
			this.response = response;
		}

		/**
		 * A filter that simply delegates to a Servlet.
		 */
		private static final class ServletFilterProxy implements Filter {

			private final Servlet delegateServlet;

			private ServletFilterProxy(Servlet servlet) {
				Assert.notNull(servlet, "servlet cannot be null");
				this.delegateServlet = servlet;
			}

			@Override
			public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
					throws IOException, ServletException {

				this.delegateServlet.service(request, response);
			}

			@Override
			public void init(FilterConfig filterConfig) throws ServletException {
			}

			@Override
			public void destroy() {
			}

			@Override
			public String toString() {
				return this.delegateServlet.toString();
			}
		}
	}

	private static class ProxyServletConfig implements ServletConfig {

		private final ServletContext servletContext;

		ProxyServletConfig(ServletContext servletContext) {
			this.servletContext = servletContext;
		}

		@Override
		public String getServletName() {
			return "serverless-proxy";
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
}
