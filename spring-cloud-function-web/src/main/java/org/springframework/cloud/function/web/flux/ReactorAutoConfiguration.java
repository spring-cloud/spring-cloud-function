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

import com.google.gson.Gson;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.boot.autoconfigure.web.HttpMessageConvertersAutoConfiguration;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.web.flux.request.FluxHandlerMethodArgumentResolver;
import org.springframework.cloud.function.web.flux.response.FluxReturnValueHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
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
@AutoConfigureBefore(HttpMessageConvertersAutoConfiguration.class)
@Import(FunctionController.class)
public class ReactorAutoConfiguration {

	@Autowired
	private ApplicationContext context;

	@Bean
	public FunctionHandlerMapping functionHandlerMapping(FunctionCatalog catalog,
			FunctionController controller) {
		return new FunctionHandlerMapping(catalog, controller);
	}

	@Bean
	@ConditionalOnMissingBean
	public StringConverter functionStringConverter(FunctionInspector inspector,
			ConfigurableListableBeanFactory beanFactory) {
		return new BasicStringConverter(inspector, beanFactory);
	}

	// TODO: remove this when https://jira.spring.io/browse/SPR-16529 is resolved
	@Bean
	public HttpMessageConverters httpMessageConverters(Gson gson) {
		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		for (HttpMessageConverter<?> converter : new HttpMessageConverters()
				.getConverters()) {
			if (converter instanceof GsonHttpMessageConverter) {
				BetterGsonHttpMessageConverter gsonConverter = new BetterGsonHttpMessageConverter();
				gsonConverter.setGson(gson);
				converters.add(gsonConverter);
			}
			else {
				converters.add(converter);
			}
		}
		return new HttpMessageConverters(false, converters);
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
				FunctionInspector inspector, Gson mapper) {
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

	private static class BasicStringConverter implements StringConverter {

		private ConversionService conversionService;
		private ConfigurableListableBeanFactory registry;
		private FunctionInspector inspector;

		public BasicStringConverter(FunctionInspector inspector,
				ConfigurableListableBeanFactory registry) {
			this.inspector = inspector;
			this.registry = registry;
		}

		@Override
		public Object convert(Object function, String value) {
			if (conversionService == null && registry != null) {
				ConversionService conversionService = this.registry
						.getConversionService();
				this.conversionService = conversionService != null ? conversionService
						: new DefaultConversionService();
			}
			Class<?> type = inspector.getInputType(function);
			return conversionService.canConvert(String.class, type)
					? conversionService.convert(value, type)
					: value;
		}

	}
}
