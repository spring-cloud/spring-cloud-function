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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

public class ProxyMvc {

	static final String MVC_RESULT_ATTRIBUTE = ProxyMvc.class.getName().concat(".MVC_RESULT_ATTRIBUTE");

	private final DispatcherServlet servlet;

	private final Filter[] filters;

	private final ConfigurableWebApplicationContext applicationContext;

	@Nullable
	private Charset defaultResponseCharacterEncoding;

	/**
	 * Private constructor, not for direct instantiation.
	 *
	 * @see org.springframework.test.web.servlet.setup.MockMvcBuilders
	 */
	public ProxyMvc(DispatcherServlet servlet, ConfigurableWebApplicationContext applicationContext) {
		Assert.notNull(servlet, "DispatcherServlet is required");
		this.applicationContext = applicationContext;

		this.servlet = servlet;
		this.filters = applicationContext.getBeansOfType(Filter.class).values().toArray(new Filter[0]);
	}

	public void stop() {
		this.applicationContext.stop();
	}

	/**
	 * The default character encoding to be applied to every response.
	 *
	 * @see org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder#defaultResponseCharacterEncoding(Charset)
	 */
	void setDefaultResponseCharacterEncoding(@Nullable Charset defaultResponseCharacterEncoding) {
		this.defaultResponseCharacterEncoding = defaultResponseCharacterEncoding;
	}

	/**
	 * Return the underlying {@link DispatcherServlet} instance that this
	 * {@code MockMvc} was initialized with.
	 * <p>
	 * This is intended for use in custom request processing scenario where a
	 * request handling component happens to delegate to the
	 * {@code DispatcherServlet} at runtime and therefore needs to be injected with
	 * it.
	 * <p>
	 * For most processing scenarios, simply use {@link MockMvc#perform}, or if you
	 * need to configure the {@code DispatcherServlet}, provide a
	 * {@link DispatcherServletCustomizer} to the {@code MockMvcBuilder}.
	 *
	 * @since 5.1
	 */
	public DispatcherServlet getDispatcherServlet() {
		return this.servlet;
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

	public ConfigurableWebApplicationContext getApplicationContext() {
		return applicationContext;
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
}
