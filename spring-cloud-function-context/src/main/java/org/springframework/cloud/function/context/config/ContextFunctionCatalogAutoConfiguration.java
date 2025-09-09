/*
 * Copyright 2016-present the original author or authors.
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import io.cloudevents.spring.messaging.CloudEventMessageConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.cloudevent.CloudEventsFunctionInvocationHelper;
import org.springframework.cloud.function.context.DefaultMessageRoutingHandler;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.core.FunctionInvocationHelper;
import org.springframework.cloud.function.json.GsonMapper;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.ContentTypeResolver;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;


/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 * @author Anshul Mehra
 * @author Soby Chacko
 * @author Chris Bono
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FunctionProperties.class)
@AutoConfigureAfter(name = {"org.springframework.cloud.function.deployer.FunctionDeployerConfiguration"})
public class ContextFunctionCatalogAutoConfiguration {

	private static Log logger = LogFactory.getLog(ContextFunctionCatalogAutoConfiguration.class);
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
					List<MessageConverter> conv = ((CompositeMessageConverter) mc).getConverters().stream().toList();
					mcList.addAll(conv);
				}
				else {
					mcList.add(mc);
				}
			}
		}

		mcList = mcList.stream()
			.filter(this::isConverterEligible)
			.collect(Collectors.toList());

		mcList.add(new JsonMessageConverter(jsonMapper));
		mcList.add(new ByteArrayMessageConverter());
		StringMessageConverter stringConverter = new StringMessageConverter();
		stringConverter.setSerializedPayloadClass(String.class);
		stringConverter.setContentTypeResolver(new ContentTypeResolver() {
			@Override
			public MimeType resolve(MessageHeaders headers) throws InvalidMimeTypeException {
				if (headers.containsKey(MessageHeaders.CONTENT_TYPE)) {
					if (headers.get(MessageHeaders.CONTENT_TYPE).toString().startsWith("text")) {
						return MimeType.valueOf("text/plain");
					}
					else {
						return MimeType.valueOf(headers.get(MessageHeaders.CONTENT_TYPE).toString());
					}
				}
				return null;
			}
		});
		mcList.add(stringConverter);

		messageConverter = new SmartCompositeMessageConverter(mcList, () -> {
			return context.getBeansOfType(MessageConverterHelper.class).values();
		});
		if (functionInvocationHelper instanceof CloudEventsFunctionInvocationHelper) {
			((CloudEventsFunctionInvocationHelper) functionInvocationHelper).setMessageConverter(messageConverter);
		}
		FunctionProperties functionProperties = context.getBean(FunctionProperties.class);
		return new BeanFactoryAwareFunctionRegistry(conversionService, messageConverter, jsonMapper, functionProperties, functionInvocationHelper);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean(RoutingFunction.FUNCTION_NAME)
	public RoutingFunction functionRouter(FunctionCatalog functionCatalog, FunctionProperties functionProperties,
								BeanFactory beanFactory, @Nullable MessageRoutingCallback routingCallback,
								@Nullable DefaultMessageRoutingHandler defaultMessageRoutingHandler) {
		if (defaultMessageRoutingHandler != null) {
			FunctionRegistration functionRegistration = new FunctionRegistration(defaultMessageRoutingHandler, RoutingFunction.DEFAULT_ROUTE_HANDLER);
			functionRegistration.type(FunctionTypeUtils.consumerType(ResolvableType.forClassWithGenerics(Message.class, Object.class).getType()));
			((FunctionRegistry) functionCatalog).register(functionRegistration);
		}
		return new RoutingFunction(functionCatalog, functionProperties, new BeanFactoryResolver(beanFactory), routingCallback);
	}

	private boolean isConverterEligible(Object messageConverter) {
		String messageConverterName = messageConverter.getClass().getName();
		if (messageConverterName.startsWith("org.springframework.cloud.")) {
			return true;
		}
		return !messageConverterName.startsWith("org.springframework.");
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(name = "io.cloudevents.spring.messaging.CloudEventMessageConverter")
	static class CloudEventsMessageConverterConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public CloudEventMessageConverter cloudEventMessageConverter() {
			return new CloudEventMessageConverter();
		}
	}

	@ComponentScan(basePackages = "${spring.cloud.function.scan.packages:functions}",
			includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = { Supplier.class, Function.class, Consumer.class }),
			excludeFilters = @Filter(type = FilterType.ANNOTATION, classes = { Configuration.class, Component.class}))
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "spring.cloud.function.scan", name = "enabled", havingValue = "true", matchIfMissing = true)
	protected static class PlainFunctionScanConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	public static class JsonMapperConfiguration {
		@Bean
		@ConditionalOnMissingBean(JsonMapper.class)
		public JsonMapper jsonMapper(ApplicationContext context) {
			String preferredMapper = context.getEnvironment().getProperty(JSON_MAPPER_PROPERTY);
			if (StringUtils.hasText(preferredMapper)) {
				if ("gson".equals(preferredMapper)) {
					return gson(context);
				}
				else if ("jackson".equals(preferredMapper)) {
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
			Assert.state(ClassUtils.isPresent("com.google.gson.Gson", ClassUtils.getDefaultClassLoader()),
				"Can not bootstrap Gson mapper since Gson is not on the classpath");
			Gson gson;
			try {
				gson = context.getBean(Gson.class);
			}
			catch (Exception e) {
				gson = new Gson();
			}
			return new GsonMapper(gson);
		}

		@SuppressWarnings("unchecked")
		private JsonMapper jackson(ApplicationContext context) {
			Assert.state(ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", ClassUtils.getDefaultClassLoader()),
				"Can not bootstrap Jackson mapper since Jackson is not on the classpath");
			ObjectMapper mapper;
			try {
				mapper = context.getBean(ObjectMapper.class).copy();
			}
			catch (Exception e) {
				mapper = new ObjectMapper();
				mapper.registerModule(new JavaTimeModule());
			}
			mapper.registerModule(new JodaModule());
			if (KotlinDetector.isKotlinPresent()) {
				try {
					if (!mapper.getRegisteredModuleIds().contains("com.fasterxml.jackson.module.kotlin.KotlinModule")) {
						Class<? extends Module> kotlinModuleClass = (Class<? extends Module>)
								ClassUtils.forName("com.fasterxml.jackson.module.kotlin.KotlinModule", ClassUtils.getDefaultClassLoader());
						Module kotlinModule = BeanUtils.instantiateClass(kotlinModuleClass);
						mapper.registerModule(kotlinModule);
					}
				}
				catch (ClassNotFoundException ex) {
					// jackson-module-kotlin not available
				}
			}
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
//			mapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			if (logger.isDebugEnabled()) {
				logger.debug("ObjectMapper configuration: " + getConfigDetails(mapper));
			}
			return new JacksonMapper(mapper);
		}

		private String getConfigDetails(ObjectMapper mapper) {
			StringBuilder sb = new StringBuilder();

			sb.append("Modules:\n");
			if (mapper.getRegisteredModuleIds().isEmpty()) {
				sb.append("\t").append("-none-").append("\n");
			}
			for (Object m : mapper.getRegisteredModuleIds()) {
				sb.append("  ").append(m).append("\n");
			}

			sb.append("\nSerialization Features:\n");
			for (SerializationFeature f : SerializationFeature.values()) {
				sb.append("\t").append(f).append(" -> ")
						.append(mapper.getSerializationConfig().hasSerializationFeatures(f.getMask()));
				if (f.enabledByDefault()) {
					sb.append(" (enabled by default)");
				}
				sb.append("\n");
			}

			sb.append("\nDeserialization Features:\n");
			for (DeserializationFeature f : DeserializationFeature.values()) {
				sb.append("\t").append(f).append(" -> ")
						.append(mapper.getDeserializationConfig().hasDeserializationFeatures(f.getMask()));
				if (f.enabledByDefault()) {
					sb.append(" (enabled by default)");
				}
				sb.append("\n");
			}

			return sb.toString();
		}
	}
}
