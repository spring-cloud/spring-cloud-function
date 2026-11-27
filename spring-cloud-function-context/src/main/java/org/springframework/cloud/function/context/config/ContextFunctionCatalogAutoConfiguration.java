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
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.SerializableString;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.util.JsonParserDelegate;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JacksonModule.SetupContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.std.DelegatingDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.CollectionType;
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
	 * Name of the property that sets the maximum number of elements of a JSON array that the
	 * default Jackson mapper deserializes into a collection (a value of zero or less disables the limit).
	 */
	static final String MAX_COLLECTION_SIZE_PROPERTY = FunctionProperties.PREFIX + ".json.max-collection-size";

	static final int DEFAULT_MAX_COLLECTION_SIZE = 1000;


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

			int maxCollectionSize = context.getEnvironment().getProperty(MAX_COLLECTION_SIZE_PROPERTY, Integer.class,
					DEFAULT_MAX_COLLECTION_SIZE);
			builder.addModule(new CollectionLimitModule(maxCollectionSize));

			ObjectMapper mapper = builder.addModule(new JodaModule())
					.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
					.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
					.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
					.configure(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION, false)
					.build();

			return new JacksonMapper(mapper);
		}
	}

	/**
	 * Wraps the deserializer that Jackson selected for a collection type (so the requested
	 * collection type and any custom deserializer are preserved) and rejects JSON arrays with more
	 * elements than the configured maximum.
	 */
	static class LimitedCollectionDeserializer extends DelegatingDeserializer {

		private final int maxItems;

		LimitedCollectionDeserializer(ValueDeserializer<?> delegate, int maxItems) {
			super(delegate);
			this.maxItems = maxItems;
		}

		@Override
		protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegatee) {
			return new LimitedCollectionDeserializer(newDelegatee, this.maxItems);
		}

		@Override
		public Object deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
			return getDelegatee().deserialize(limited(p), ctxt);
		}

		@Override
		@SuppressWarnings("unchecked")
		public Object deserialize(JsonParser p, DeserializationContext ctxt, Object intoValue)
				throws JacksonException {
			return ((ValueDeserializer<Object>) getDelegatee()).deserialize(limited(p), ctxt, intoValue);
		}

		private JsonParser limited(JsonParser p) {
			return this.maxItems > 0 && p.currentToken() == JsonToken.START_ARRAY
					? new ItemCountingParser(p, this.maxItems) : p;
		}

	}

	/**
	 * Counts the elements of the array the parser is positioned at, without buffering them.
	 */
	private static final class ItemCountingParser extends JsonParserDelegate {

		private final int maxItems;

		private int depth = 1;

		private int count;

		ItemCountingParser(JsonParser delegate, int maxItems) {
			super(delegate);
			this.maxItems = maxItems;
		}

		@Override
		public JsonToken nextToken() {
			return track(super.nextToken());
		}

		@Override
		public JsonToken nextValue() {
			return track(super.nextValue());
		}

		@Override
		public String nextName() {
			String name = super.nextName();
			track(currentToken());
			return name;
		}

		@Override
		public boolean nextName(SerializableString str) {
			boolean matched = super.nextName(str);
			track(currentToken());
			return matched;
		}

		@Override
		public int nextNameMatch(PropertyNameMatcher matcher) {
			int match = super.nextNameMatch(matcher);
			track(currentToken());
			return match;
		}

		@Override
		public JsonParser skipChildren() {
			if (currentToken() != null && currentToken().isStructStart()) {
				super.skipChildren();
				this.depth--;
			}
			return this;
		}

		private JsonToken track(JsonToken token) {
			if (token == null || token == JsonToken.PROPERTY_NAME) {
				return token;
			}
			if (token.isStructEnd()) {
				this.depth--;
				return token;
			}
			if (this.depth == 1 && ++this.count > this.maxItems) {
				throw DatabindException.from(delegate(),
						"Collection size exceeds the limit of " + this.maxItems + " elements (configurable with '"
								+ MAX_COLLECTION_SIZE_PROPERTY + "')");
			}
			if (token.isStructStart()) {
				this.depth++;
			}
			return token;
		}

	}

	static class CollectionLimitModule extends SimpleModule {

		private final int maxItems;

		CollectionLimitModule(int maxItems) {
			this.maxItems = maxItems;
		}

		@Override
		public void setupModule(SetupContext context) {
			super.setupModule(context);
			context.addDeserializerModifier(new ValueDeserializerModifier() {
				@Override
				public ValueDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config,
						CollectionType type, BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer) {
					return new LimitedCollectionDeserializer(deserializer, CollectionLimitModule.this.maxItems);
				}
			});
		}

	}
}
