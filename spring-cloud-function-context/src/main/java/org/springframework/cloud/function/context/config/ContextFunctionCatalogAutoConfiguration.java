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

import com.google.gson.Gson;
import io.cloudevents.spring.messaging.CloudEventMessageConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTimeZone;
import org.joda.time.tz.UTCProvider;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.datatype.joda.JodaModule;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;


/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 * @author Anshul Mehra
 * @author Soby Chacko
 * @author Chris Bono
 * @author Roman Akentev
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FunctionProperties.class)
@AutoConfigureAfter(name = {"org.springframework.cloud.function.deployer.FunctionDeployerConfiguration"})
public class ContextFunctionCatalogAutoConfiguration {


	/**
	 * The name of the property to specify desired JSON mapper. Available values are `jackson' and 'gson'.
	 */
	public static final String JSON_MAPPER_PROPERTY = "spring.cloud.function.preferred-json-mapper";
	private static final Log logger = LogFactory
			.getLog(ContextFunctionCatalogAutoConfiguration.class);

	@Bean
	public FunctionRegistry functionCatalog(List<MessageConverter> messageConverters, JsonMapper jsonMapper,
											ConfigurableApplicationContext context, @Nullable FunctionInvocationHelper<Message<?>> functionInvocationHelper,
											FunctionProperties functionProperties,
											@Value("${spring.cloud.function.registry.cache-size:1000}") int wrappedFunctionDefinitionsCacheSize) {
		ConversionService existing = context.getBeanFactory().getConversionService();
		ConfigurableConversionService conversionService = (existing instanceof ConfigurableConversionService ccs)
				? ccs
				: new DefaultConversionService();
		Map<String, GenericConverter> converters = context.getBeansOfType(GenericConverter.class);
		for (GenericConverter converter : converters.values()) {
			conversionService.addConverter(converter);
		}

		SmartCompositeMessageConverter messageConverter;
		List<MessageConverter> mcList = new ArrayList<>();

		if (!CollectionUtils.isEmpty(messageConverters)) {
			for (MessageConverter mc : messageConverters) {
				if (mc instanceof CompositeMessageConverter composite) {
					mcList.addAll(composite.getConverters());
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
		stringConverter.setContentTypeResolver(headers -> {
			if (headers != null) {
				Object contentType = headers.get(MessageHeaders.CONTENT_TYPE);
				if (contentType != null) {
					String typeStr = contentType.toString();
					return typeStr.startsWith("text") ? MimeTypeUtils.TEXT_PLAIN
							: MimeType.valueOf(typeStr);
				}
			}
			return null;
		});
		mcList.add(stringConverter);

		messageConverter = new SmartCompositeMessageConverter(mcList,
				() -> context.getBeansOfType(MessageConverterHelper.class).values());
		if (functionInvocationHelper instanceof CloudEventsFunctionInvocationHelper cloudEventsFunctionInvocationHelper) {
			cloudEventsFunctionInvocationHelper.setMessageConverter(messageConverter);
		}
		return new BeanFactoryAwareFunctionRegistry(conversionService, messageConverter, jsonMapper, functionProperties, functionInvocationHelper, wrappedFunctionDefinitionsCacheSize);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean(RoutingFunction.FUNCTION_NAME)
	public RoutingFunction functionRouter(FunctionCatalog functionCatalog, FunctionProperties functionProperties,
								BeanFactory beanFactory, @Nullable MessageRoutingCallback routingCallback,
								@Nullable DefaultMessageRoutingHandler defaultMessageRoutingHandler) {
		if (defaultMessageRoutingHandler != null) {
			FunctionRegistration functionRegistration = new FunctionRegistration<>(defaultMessageRoutingHandler, RoutingFunction.DEFAULT_ROUTE_HANDLER);
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
				if (ClassUtils.isPresent("tools.jackson.databind.ObjectMapper", ClassUtils.getDefaultClassLoader())) {
					return jackson(context);
				}
				else if (ClassUtils.isPresent("com.google.gson.Gson", ClassUtils.getDefaultClassLoader())) {
					return gson(context);
				}
			}
			throw new IllegalStateException("Failed to configure JsonMapper. Neither jackson nor gson are present on the classpath");
		}

		private JsonMapper gson(ApplicationContext context) {
			Assert.state(ClassUtils.isPresent("com.google.gson.Gson", ClassUtils.getDefaultClassLoader()),
				"Can not bootstrap Gson mapper since Gson is not on the classpath");
			Gson gson;
			try {
				gson = context.getBean(Gson.class);
			}
			catch (Exception e) {
				logger.warn(
					"Failed to obtain a Gson bean from context. "
						+ "Falling back to default Gson configuration.",
					e);
				gson = new Gson();
			}
			return new GsonMapper(gson);
		}

		private JsonMapper jackson(ApplicationContext context) {
			Assert.state(ClassUtils.isPresent("tools.jackson.databind.ObjectMapper", ClassUtils.getDefaultClassLoader()),
				"Can not bootstrap Jackson mapper since Jackson is not on the classpath");

			MapperBuilder<?, ?> builder;
			try {
				builder = context.getBean(ObjectMapper.class).rebuild();
			}
			catch (Exception ex) {
				logger.warn(
					"Unexpected error while attempting to rebuild ObjectMapper. "
						+ "Falling back to default Jackson configuration.",
					ex);
			builder = tools.jackson.databind.json.JsonMapper.builder();
			DateTimeZone.setProvider(new UTCProvider());
		}

			if (KotlinDetector.isKotlinPresent()) {
				try {
					Class<?> rawClass = ClassUtils.forName(
							"tools.jackson.module.kotlin.KotlinModule",
							ClassUtils.getDefaultClassLoader());
					if (JacksonModule.class.isAssignableFrom(rawClass)) {
						Class<? extends JacksonModule> kotlinModuleClass = rawClass
								.asSubclass(JacksonModule.class);
						builder.addModule(BeanUtils.instantiateClass(kotlinModuleClass));
					}
					else {
						logger.warn(
								"Kotlin Jackson module ('tools.jackson.module.kotlin.KotlinModule') is present on the classpath, "
										+ "but is not assignable to JacksonModule. Check for Jackson version mismatches or classloader conflicts.");
					}
				}
				catch (ClassNotFoundException ex) {
					logger.warn(
							"Kotlin is present on the classpath, but the Jackson Kotlin module is not available. "
									+ "Consider adding 'jackson-module-kotlin' to your dependencies to avoid serialization issues with Kotlin classes.");
				}
			}

			ObjectMapper mapper = builder.addModule(new JodaModule())
					.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
					.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
					.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
					.configure(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION, false)
					.build();

			return new JacksonMapper(mapper);
		}
	}
}
