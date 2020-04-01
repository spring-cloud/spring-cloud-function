/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.json.GsonMapper;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.util.CollectionUtils;


/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 * @author Anshul Mehra
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(FunctionCatalog.class)
@EnableConfigurationProperties(FunctionProperties.class)
public class ContextFunctionCatalogAutoConfiguration {

	static final String PREFERRED_MAPPER_PROPERTY = "spring.http.converters.preferred-json-mapper";

	@Bean
	public FunctionRegistry functionCatalog(List<MessageConverter> messageConverters, JsonMapper jsonMapper, ConfigurableApplicationContext context) {
		ConfigurableConversionService conversionService = (ConfigurableConversionService) context.getBeanFactory().getConversionService();
		Map<String, GenericConverter> converters = context.getBeansOfType(GenericConverter.class);
		for (GenericConverter converter : converters.values()) {
			conversionService.addConverter(converter);
		}

		CompositeMessageConverter messageConverter = null;
		List<MessageConverter> mcList = new ArrayList<>();

		if (!CollectionUtils.isEmpty(messageConverters)) {
			for (MessageConverter mc : messageConverters) {
				if (mc instanceof CompositeMessageConverter) {
					mcList.addAll(((CompositeMessageConverter) mc).getConverters());
				}
				else {
					mcList.add(mc);
				}
			}
		}

		mcList = mcList.stream()
				.filter(c -> isConverterEligible(c))
				.map(converter -> {
					return converter instanceof AbstractMessageConverter
							? NegotiatingMessageConverterWrapper.wrap((AbstractMessageConverter) converter)
									: converter;
				})
				.collect(Collectors.toList());
		mcList.add(NegotiatingMessageConverterWrapper.wrap(new JsonMessageConverter(jsonMapper)));
		mcList.add(NegotiatingMessageConverterWrapper.wrap(new ByteArrayMessageConverter()));
		mcList.add(NegotiatingMessageConverterWrapper.wrap(new StringMessageConverter()));

		if (!CollectionUtils.isEmpty(mcList)) {
			messageConverter = new CompositeMessageConverter(mcList);
		}

		return new BeanFactoryAwareFunctionRegistry(conversionService, messageConverter);
	}

	@Bean(RoutingFunction.FUNCTION_NAME)
	RoutingFunction functionRouter(FunctionCatalog functionCatalog, FunctionInspector functionInspector, FunctionProperties functionProperties) {
		return new RoutingFunction(functionCatalog, functionInspector, functionProperties);
	}

	private boolean isConverterEligible(Object messageConverter) {
		String messageConverterName = messageConverter.getClass().getName();
		if (messageConverterName.startsWith("org.springframework.cloud.")) {
			return true;
		}
		else if (!messageConverterName.startsWith("org.springframework.")) {
			return true;
		}
		return false;
	}

	@Configuration(proxyBeanMethods = false)
	@ComponentScan(basePackages = "${spring.cloud.function.scan.packages:functions}", //
			includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
					classes = { Supplier.class, Function.class, Consumer.class }))
	@ConditionalOnProperty(prefix = "spring.cloud.function.scan", name = "enabled", havingValue = "true",
			matchIfMissing = true)
	protected static class PlainFunctionScanConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Gson.class)
	@ConditionalOnProperty(name = PREFERRED_MAPPER_PROPERTY, havingValue = "gson", matchIfMissing = false)
	protected static class GsonConfiguration {

		@Bean
		public GsonMapper jsonMapper(@Nullable Gson gson) {
			if (gson == null) {
				gson = new Gson();
			}
			return new GsonMapper(gson);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(ObjectMapper.class)
	@ConditionalOnProperty(name = PREFERRED_MAPPER_PROPERTY, havingValue = "jackson", matchIfMissing = true)
	protected static class JacksonConfiguration {

		@Bean
		public JacksonMapper jsonMapper(@Nullable ObjectMapper mapper) {
			if (mapper == null) {
				mapper = new ObjectMapper();
			}
			return new JacksonMapper(mapper);
		}

	}

}
