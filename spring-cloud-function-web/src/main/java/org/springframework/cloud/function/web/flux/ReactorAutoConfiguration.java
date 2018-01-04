/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.web.flux;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.core.FunctionCatalog;
import org.springframework.cloud.function.web.flux.request.FluxHandlerMethodArgumentResolver;
import org.springframework.cloud.function.web.flux.response.FluxReturnValueHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 * @author Mark Fisher
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass({ Flux.class, AsyncHandlerMethodReturnValueHandler.class })
public class ReactorAutoConfiguration {

	@Autowired
	private ApplicationContext context;

	@Bean
	public FunctionHandlerMapping functionHandlerMapping(FunctionCatalog catalog,
			FunctionInspector inspector) {
		return new FunctionHandlerMapping(catalog, inspector);
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.core.ReactiveAdapter")
	protected static class FluxReturnValueConfiguration {
		@Bean
		public FluxReturnValueHandler fluxReturnValueHandler(FunctionInspector inspector,
				HttpMessageConverters converters) {
			return new FluxReturnValueHandler(inspector, converters.getConverters());
		}
	}

	@Configuration
	protected static class FluxArgumentResolverConfiguration {
		@Bean
		public FluxHandlerMethodArgumentResolver fluxHandlerMethodArgumentResolver(
				FunctionInspector inspector, ObjectMapper mapper) {
			return new FluxHandlerMethodArgumentResolver(inspector, mapper);
		}
	}

	@Bean
	public SmartInitializingSingleton fluxRequestMappingHandlerAdapterProcessor(
			RequestMappingHandlerAdapter adapter,
			FluxHandlerMethodArgumentResolver resolver) {
		return new SmartInitializingSingleton() {

			@Override
			public void afterSingletonsInstantiated() {
				List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(
						adapter.getArgumentResolvers());
				resolvers.add(0, resolver);
				adapter.setArgumentResolvers(resolvers);
				if (!ClassUtils.isPresent("org.springframework.core.ReactiveAdapter",
						null)) {
					List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(
							adapter.getReturnValueHandlers());
					handlers.add(0, context.getBean(FluxReturnValueHandler.class));
					adapter.setReturnValueHandlers(handlers);
				}
			}

		};
	}
}
