/*
 * Copyright 2016-2021 the original author or authors.
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
import io.cloudevents.spring.messaging.CloudEventMessageConverter;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.cloudevent.CloudEventsFunctionInvocationHelper;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry;
import org.springframework.cloud.function.context.converter.avro.AvroSchemaMessageConverter;
import org.springframework.cloud.function.context.converter.avro.AvroSchemaServiceManagerImpl;
import org.springframework.cloud.function.core.FunctionInvocationHelper;
import org.springframework.cloud.function.json.GsonMapper;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.utils.PrimitiveTypesFromStringMessageConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;


/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 * @author Anshul Mehra
 * @author Soby Chacko
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(FunctionCatalog.class)
@EnableConfigurationProperties(FunctionProperties.class)
@AutoConfigureAfter(name = {"org.springframework.cloud.function.deployer.FunctionDeployerConfiguration"})
public class ContextFunctionCatalogAutoConfiguration {

	@Deprecated
	static final String PREFERRED_MAPPER_PROPERTY = "spring.http.converters.preferred-json-mapper";

	/**
	 * The name of the property to specify desired JSON mapper. Available values are `jackson' and 'gson'.
	 */
	public static final String JSON_MAPPER_PROPERTY = "spring.cloud.function.preferred-json-mapper";

	@Bean
	public FunctionRegistry functionCatalog(List<MessageConverter> messageConverters, JsonMapper jsonMapper,
											ConfigurableApplicationContext context, @Nullable FunctionInvocationHelper<Message<?>> functionInvocationHelper) {
		ConfigurableConversionService conversionService = (ConfigurableConversionService) context.getBeanFactory().getConversionService();
		if (conversionService == null) {
			conversionService = new DefaultConversionService();
		}
		Map<String, GenericConverter> converters = context.getBeansOfType(GenericConverter.class);
		for (GenericConverter converter : converters.values()) {
			conversionService.addConverter(converter);
		}

		SmartCompositeMessageConverter messageConverter = null;
		List<MessageConverter> mcList = new ArrayList<>();

		if (!CollectionUtils.isEmpty(messageConverters)) {
			for (MessageConverter mc : messageConverters) {
				if (mc instanceof CompositeMessageConverter) {
					List<MessageConverter> conv = ((CompositeMessageConverter) mc).getConverters().stream()
						.collect(Collectors.toList());
					mcList.addAll(conv);
				}
				else {
					mcList.add(mc);
				}
			}
		}

		mcList = mcList.stream()
			.filter(c -> isConverterEligible(c))
			.collect(Collectors.toList());

		mcList.add(new JsonMessageConverter(jsonMapper));
		mcList.add(new ByteArrayMessageConverter());
		mcList.add(new StringMessageConverter());
		mcList.add(new PrimitiveTypesFromStringMessageConverter(conversionService));

		if (!CollectionUtils.isEmpty(mcList)) {
			messageConverter = new SmartCompositeMessageConverter(mcList);
			if (functionInvocationHelper instanceof CloudEventsFunctionInvocationHelper) {
				((CloudEventsFunctionInvocationHelper) functionInvocationHelper).setMessageConverter(messageConverter);
			}
		}
		FunctionProperties functionProperties = context.getBean(FunctionProperties.class);
		return new BeanFactoryAwareFunctionRegistry(conversionService, messageConverter, jsonMapper, functionProperties, functionInvocationHelper);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnClass(name = "org.apache.avro.Schema")
	public MessageConverter avroSchemaMessageConverter() {
		return new AvroSchemaMessageConverter(new AvroSchemaServiceManagerImpl());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnClass(name = "io.cloudevents.spring.messaging.CloudEventMessageConverter")
	public MessageConverter cloudEventMessageConverter() {
		return new CloudEventMessageConverter();
	}

	@Bean(RoutingFunction.FUNCTION_NAME)
	RoutingFunction functionRouter(FunctionCatalog functionCatalog, FunctionProperties functionProperties,
								BeanFactory beanFactory, @Nullable MessageRoutingCallback routingCallback) {
		return new RoutingFunction(functionCatalog, functionProperties, new BeanFactoryResolver(beanFactory), routingCallback);
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
			classes = {Supplier.class, Function.class, Consumer.class}))
	@ConditionalOnProperty(prefix = "spring.cloud.function.scan", name = "enabled", havingValue = "true",
		matchIfMissing = true)
	protected static class PlainFunctionScanConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	public static class JsonMapperConfiguration {
		@Bean
		public JsonMapper jsonMapper(ApplicationContext context) {
			String preferredMapper = context.getEnvironment().containsProperty(JSON_MAPPER_PROPERTY)
				? context.getEnvironment().getProperty(JSON_MAPPER_PROPERTY)
				: context.getEnvironment().getProperty(PREFERRED_MAPPER_PROPERTY);
			if (StringUtils.hasText(preferredMapper)) {
				if ("gson".equals(preferredMapper) && ClassUtils.isPresent("com.google.gson.Gson", null)) {
					return gson(context);
				}
				else if ("jackson".equals(preferredMapper) && ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", null)) {
					return jackson(context);
				}
			}
			else {
				if (ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", null)) {
					return jackson(context);
				}
				else if (ClassUtils.isPresent("com.google.gson.Gson", null)) {
					return gson(context);
				}
			}
			throw new IllegalStateException("Failed to configure JsonMapper. Neither jackson nor gson are present on the claspath");
		}

		private JsonMapper gson(ApplicationContext context) {
			Gson gson;
			try {
				gson = context.getBean(Gson.class);
			}
			catch (Exception e) {
				gson = new Gson();
			}
			return new GsonMapper(gson);
		}

		private JsonMapper jackson(ApplicationContext context) {
			ObjectMapper mapper;
			try {
				mapper = context.getBean(ObjectMapper.class);
			}
			catch (Exception e) {
				mapper = new ObjectMapper();
			}
			return new JacksonMapper(mapper);
		}
	}
}
