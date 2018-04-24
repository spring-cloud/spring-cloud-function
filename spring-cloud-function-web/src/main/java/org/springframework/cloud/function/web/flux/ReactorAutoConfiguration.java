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
import com.google.gson.Gson;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.web.flux.request.FluxHandlerMethodArgumentResolver;
import org.springframework.cloud.function.web.flux.response.FluxReturnValueHandler;
import org.springframework.cloud.function.web.util.GsonMapper;
import org.springframework.cloud.function.web.util.JacksonMapper;
import org.springframework.cloud.function.web.util.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
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
@Import(FunctionController.class)
@AutoConfigureAfter({ JacksonAutoConfiguration.class, GsonAutoConfiguration.class })
public class ReactorAutoConfiguration {

	@Autowired
	private ApplicationContext context;

	static final String PREFERRED_MAPPER_PROPERTY = "spring.http.converters.preferred-json-mapper";

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
				FunctionInspector inspector, JsonMapper mapper) {
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

	@Configuration
	@ConditionalOnClass(Gson.class)
	@Conditional(PreferGsonOrMissingJacksonCondition.class)
	protected static class GsonConfiguration {
		@Bean
		public GsonMapper jsonMapper(Gson gson) {
			return new GsonMapper(gson);
		}
	}

	@Configuration
	@ConditionalOnClass(ObjectMapper.class)
	@ConditionalOnProperty(name = ReactorAutoConfiguration.PREFERRED_MAPPER_PROPERTY, havingValue = "jackson", matchIfMissing = true)
	protected static class JacksonConfiguration {
		@Bean
		public JacksonMapper jsonMapper(ObjectMapper mapper) {
			return new JacksonMapper(mapper);
		}
	}

	private static class PreferGsonOrMissingJacksonCondition extends AnyNestedCondition {

		PreferGsonOrMissingJacksonCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(name = ReactorAutoConfiguration.PREFERRED_MAPPER_PROPERTY, havingValue = "gson", matchIfMissing = false)
		static class GsonPreferred {

		}

		@ConditionalOnMissingBean(ObjectMapper.class)
		static class JacksonMissing {

		}

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
