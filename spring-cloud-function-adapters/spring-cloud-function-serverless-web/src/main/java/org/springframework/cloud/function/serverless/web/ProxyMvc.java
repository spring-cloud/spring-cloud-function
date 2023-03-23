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
import java.util.concurrent.CountDownLatch;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
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

	private static Log LOG = LogFactory.getLog(ProxyMvc.class);

	static final String MVC_RESULT_ATTRIBUTE = ProxyMvc.class.getName().concat(".MVC_RESULT_ATTRIBUTE");

	private final DispatcherServlet dispatcher;

	private final ConfigurableWebApplicationContext applicationContext;

	private ServletContext servletContext;

	private volatile boolean initialized;

	public ConfigurableWebApplicationContext getApplicationContext() {
		return this.applicationContext;
	}

	public ServletContext getServletContext() {
		return this.servletContext;
	}

	public static ProxyMvc INSTANCE(ConfigurableWebApplicationContext applpicationContext) {
		ProxyServletContext servletContext = new ProxyServletContext();
		applpicationContext.setServletContext(servletContext);
		DispatcherServlet dispatcher = new DispatcherServlet(applpicationContext);
		ServletRegistration.Dynamic reg = servletContext.addServlet("dispatcherServlet", dispatcher);
		reg.setLoadOnStartup(1);

		ProxyMvc mvc = new ProxyMvc(dispatcher, applpicationContext);
		mvc.servletContext = servletContext;
		return mvc;
	}

	public static ProxyMvc INSTANCE(Class<?>... componentClasses) {
		GenericWebApplicationContext applpicationContext = new GenericWebApplicationContext();
		AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(applpicationContext);
		if (!ObjectUtils.isEmpty(componentClasses)) {
			reader.register(componentClasses);
		}
		return INSTANCE(applpicationContext);
	}

	/**
	 * Private constructor, not for direct instantiation.
	 */
	ProxyMvc(DispatcherServlet dispatcher, ConfigurableWebApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
		this.dispatcher = dispatcher;
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
		this.service(request, response, (CountDownLatch) null);
	}

	public void service(HttpServletRequest request, HttpServletResponse response, CountDownLatch latch) throws Exception {
		synchronized (this) {
			if (!this.initialized) {
				this.dispatcher.init(new ProxyServletConfig(this.servletContext));
				this.initialized = true;
			}
		}

		ProxyFilterChain filterChain = new ProxyFilterChain(this.dispatcher);
		filterChain.doFilter(request, response);

		if (latch != null) {
			latch.countDown();
		}
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
		ProxyFilterChain(DispatcherServlet servlet) {
			List<Filter> filters = new ArrayList<>();
			servlet.getServletContext().getFilterRegistrations().values().forEach(fr -> filters.add(((ProxyFilterRegistration)fr).getFilter()));
			Assert.notNull(filters, "filters cannot be null");
			Assert.noNullElements(filters, "filters cannot contain null values");
			this.filters = initFilterList(servlet, filters.toArray(new Filter[] {}));
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

				try {
					if (((HttpServletResponse) response).getStatus() != HttpStatus.OK.value() && request instanceof ProxyHttpServletRequest) {
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_STATUS_CODE, ((HttpServletResponse) response).getStatus());
						this.setErrorMessageAttribute((ProxyHttpServletRequest) request, (ProxyHttpServletResponse) response, null);
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_REQUEST_URI, ((HttpServletRequest) request).getRequestURI());

						((ProxyHttpServletRequest) request).setRequestURI("/error");
						this.delegateServlet.service(request, response);
					}
					else {
						this.delegateServlet.service(request, response);
						if (((HttpServletResponse) response).getStatus() != HttpStatus.OK.value() && request instanceof ProxyHttpServletRequest) {
							((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_STATUS_CODE, ((HttpServletResponse) response).getStatus());
							this.setErrorMessageAttribute((ProxyHttpServletRequest) request, (ProxyHttpServletResponse) response, null);
							((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_REQUEST_URI, ((HttpServletRequest) request).getRequestURI());

							((ProxyHttpServletRequest) request).setRequestURI("/error");
							this.delegateServlet.service(request, response);
						}
					}
				}
				catch (Exception e) {
					if (request instanceof ProxyHttpServletRequest) {
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.INTERNAL_SERVER_ERROR);
						this.setErrorMessageAttribute((ProxyHttpServletRequest) request, (ProxyHttpServletResponse) response, e);
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, e);
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_REQUEST_URI, ((HttpServletRequest) request).getRequestURI());
						((ProxyHttpServletRequest) request).setRequestURI("/error");
					}

					LOG.error("Failed processing the request to: " + ((HttpServletRequest) request).getRequestURI(), e);

					this.delegateServlet.service(request, response);
				}
			}

			private void setErrorMessageAttribute(ProxyHttpServletRequest request, ProxyHttpServletResponse response, Exception exception) {
				if (exception != null && StringUtils.hasText(exception.getMessage())) {
					request.setAttribute(RequestDispatcher.ERROR_MESSAGE, exception.getMessage());
				}
				else if (StringUtils.hasText(response.getErrorMessage())) {
					request.setAttribute(RequestDispatcher.ERROR_MESSAGE, response.getErrorMessage());
				}
				else {
					request.setAttribute(RequestDispatcher.ERROR_MESSAGE, HttpStatus.valueOf(response.getStatus()).getReasonPhrase());

				}
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
			return "spring-serverless-proxy";
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
