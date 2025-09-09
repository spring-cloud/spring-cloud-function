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

package org.springframework.cloud.function.test.app;

import java.io.IOException;
import java.util.Collections;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.RequestCacheAwareFilter;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;


@Configuration
@Import({ PetsController.class, FreemarkerController.class })
@EnableWebSecurity
@EnableAutoConfiguration
public class PetStoreSpringAppConfig {

	/*
	 * Create required HandlerMapping, to avoid several default HandlerMapping
	 * instances being created
	 */
	@Bean
	public HandlerMapping handlerMapping() {
		return new RequestMappingHandlerMapping();
	}

	/*
	 * Create required HandlerAdapter, to avoid several default HandlerAdapter
	 * instances being created
	 */
	@Bean
	public HandlerAdapter handlerAdapter() {
		return new RequestMappingHandlerAdapter();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, SimpleFilter simpleFilter,
			AnotherFilter anotherFilter) throws Exception {
		http
		.csrf(csrf -> csrf.disable())
		.cors(cors -> cors.disable())
		.addFilterBefore(new GenericFilterBean() {
			@Override
			public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				SecurityContext securityContext = SecurityContextHolder.getContext();
				securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user", "password",
						Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))));
				HttpSession session = ((HttpServletRequest) request).getSession();
				session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
				chain.doFilter(request, response);
			}
		}, SecurityContextHolderFilter.class)
		.securityMatcher("/foo/deny")
		.authorizeHttpRequests(auth -> {
			auth.anyRequest().hasRole("FOO");
		})
		.addFilterAfter(simpleFilter, LogoutFilter.class)
		.addFilterAfter(anotherFilter, RequestCacheAwareFilter.class)
		.exceptionHandling(f -> f.accessDeniedHandler(new MyAccessDeinedHandler()));
		return http.build();
	}

	@Bean
	public FilterRegistrationBean<SimpleFilter> simpleFilterRegistration(SimpleFilter simpleFilter) {
		FilterRegistrationBean<SimpleFilter> registration = new FilterRegistrationBean<>(simpleFilter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public FilterRegistrationBean<AnotherFilter> anotherFilterRegistration(AnotherFilter simpleFilter) {
		FilterRegistrationBean<AnotherFilter> registration = new FilterRegistrationBean<>(simpleFilter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public SimpleFilter simpleFilter() {
		return new SimpleFilter();
	}

	@Bean
	public AnotherFilter anotherFilter() {
		return new AnotherFilter();
	}

	public static class SimpleFilter extends OncePerRequestFilter {
		/**
		 *
		 */
		public boolean invoked;

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, IOException {
			if (invoked) {
				throw new IllegalStateException("Filter has already been invoked");
			}
			else {
				invoked = true;
			}

			filterChain.doFilter(request, response);
		}
	}

	public static class AnotherFilter extends OncePerRequestFilter {

		/**
		 *
		 */
		public boolean invoked;
		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, IOException {
			if (invoked) {
				throw new IllegalStateException("Filter has already been invoked");
			}
			else {
				invoked = true;
			}
			filterChain.doFilter(request, response);
		}
	}

	public static class MyAccessDeinedHandler implements AccessDeniedHandler {

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response,
				AccessDeniedException accessDeniedException) throws IOException, ServletException {
			response.sendError(403, "Can't touch this");
		}

	}
}
