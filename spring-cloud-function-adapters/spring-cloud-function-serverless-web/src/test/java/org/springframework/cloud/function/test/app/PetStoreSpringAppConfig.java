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

package org.springframework.cloud.function.test.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;


@Configuration
@Import({ PetsController.class })
@EnableWebSecurity
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
	public BeanPostProcessor post() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if (beanName.equals("securityFilterChain")) {
					DefaultSecurityFilterChain chain = (DefaultSecurityFilterChain) bean;
					ArrayList<Filter> filters = new ArrayList<>();
					chain.getFilters().forEach(f -> {
						if (!(f instanceof CsrfFilter)) {
							filters.add(f);
						}
					});
					bean = new DefaultSecurityFilterChain(chain.getRequestMatcher(), filters);
				}
				//System.out.println(beanName);
				return bean;
			}
		};
	}


	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.addFilterBefore(new GenericFilterBean() {

				@Override
				public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
						throws IOException, ServletException {
					SecurityContext securityContext = SecurityContextHolder.getContext();
					securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user", "password",
							Collections.singleton(new SimpleGrantedAuthority("USER"))));
					HttpSession session = ((HttpServletRequest) request).getSession();
					session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
					chain.doFilter(request, response);
				}
			}, SecurityContextHolderFilter.class)
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers("/", "/pets", "/pets/").permitAll()
				.anyRequest().authenticated()
			)
			.logout((logout) -> logout.permitAll());

		return http.build();
	}

	@Bean
	public Filter filter() {
		return new Filter() {
			@Override
			public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				System.out.println("FILTER ===> Hello from: " + request.getLocalAddr());
				chain.doFilter(request, response);
			}
		};
	}

}
