/*
 * Copyright 2012-2019 the original author or authors.
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.utils.PrimitiveTypesFromStringMessageConverter;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 */
public class ContextFunctionCatalogInitializer implements ApplicationContextInitializer<GenericApplicationContext> {

	/**
	 * Property name for ignoring pre initilizer.
	 */
	public static final String IGNORE_BACKGROUNDPREINITIALIZER_PROPERTY_NAME = "spring.backgroundpreinitializer.ignore";

	/**
	 * Flag for enabling the context function catalog initializer.
	 */
	public static boolean enabled = true;

	@Override
	public void initialize(GenericApplicationContext applicationContext) {
		if (enabled
				&& applicationContext.getEnvironment().getProperty("spring.functional.enabled", Boolean.class, false)) {
			ContextFunctionCatalogBeanRegistrar registrar = new ContextFunctionCatalogBeanRegistrar(applicationContext);
			applicationContext.addBeanFactoryPostProcessor(registrar);
		}
	}

	static class ContextFunctionCatalogBeanRegistrar implements BeanDefinitionRegistryPostProcessor {

		private GenericApplicationContext context;

		ContextFunctionCatalogBeanRegistrar(GenericApplicationContext applicationContext) {
			this.context = applicationContext;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		}

		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
			try {
				register(registry, this.context.getDefaultListableBeanFactory());
			}
			catch (BeansException e) {
				throw e;
			}
			catch (RuntimeException e) {
				throw e;
			}
			catch (Exception e) {
				throw new BeanCreationException("Cannot register from " + getClass(), e);
			}
		}

		protected void register(BeanDefinitionRegistry registry, ConfigurableListableBeanFactory factory)
				throws Exception {

			performPreinitialization();

			if (this.context.getBeanFactory().getBeanNamesForType(PropertySourcesPlaceholderConfigurer.class, false,
					false).length == 0) {
				this.context.registerBean(PropertySourcesPlaceholderConfigurer.class,
						() -> PropertyPlaceholderAutoConfiguration.propertySourcesPlaceholderConfigurer());
			}

			if (!this.context.getBeanFactory()
					.containsBeanDefinition(AnnotationConfigUtils.CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
				// Switch off the ConfigurationClassPostProcessor
				this.context.registerBean(AnnotationConfigUtils.CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME,
						DummyProcessor.class, () -> new DummyProcessor());
				// But switch on other annotation processing
				AnnotationConfigUtils.registerAnnotationConfigProcessors(this.context);
			}
			ConfigurationPropertiesBindingPostProcessor.register(registry);

			String preferredMapper = context.getEnvironment().containsProperty(ContextFunctionCatalogAutoConfiguration.JSON_MAPPER_PROPERTY)
					? context.getEnvironment().getProperty(ContextFunctionCatalogAutoConfiguration.JSON_MAPPER_PROPERTY)
					: context.getEnvironment().getProperty(ContextFunctionCatalogAutoConfiguration.PREFERRED_MAPPER_PROPERTY);


			if (ClassUtils.isPresent("com.google.gson.Gson", null) && "gson".equals(preferredMapper)) {
				if (this.context.getBeanFactory().getBeanNamesForType(Gson.class, false, false).length == 0) {
					this.context.registerBean(Gson.class, () -> new Gson());
				}
				this.context.registerBean(JsonMapper.class, () -> new ContextFunctionCatalogAutoConfiguration.JsonMapperConfiguration().jsonMapper(this.context));
			}
			else if (ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", null)) {
				if (this.context.getBeanFactory().getBeanNamesForType(ObjectMapper.class, false, false).length == 0) {
					this.context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
				}
				this.context.registerBean(JsonMapper.class, () -> new ContextFunctionCatalogAutoConfiguration.JsonMapperConfiguration().jsonMapper(this.context));

			}

			String basePackage = this.context.getEnvironment().getProperty("spring.cloud.function.scan.packages",
					"functions");
			if (this.context.getEnvironment().getProperty("spring.cloud.function.scan.enabled", Boolean.class, true)
					&& new ClassPathResource(basePackage.replace(".", "/")).exists()) {
				ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(this.context, false,
						this.context.getEnvironment(), this.context);
				scanner.addIncludeFilter(new AssignableTypeFilter(Function.class));
				scanner.addIncludeFilter(new AssignableTypeFilter(Supplier.class));
				scanner.addIncludeFilter(new AssignableTypeFilter(Consumer.class));
				for (BeanDefinition bean : scanner.findCandidateComponents(basePackage)) {
					String name = bean.getBeanClassName();
					Class<?> type = ClassUtils.resolveClassName(name, this.context.getClassLoader());
					this.context.registerBeanDefinition(name, bean);
					this.context.registerBean("registration_" + name, FunctionRegistration.class,
							() -> new FunctionRegistration<>(this.context.getBean(name), name).type(type));
				}
			}

			if (this.context.getBeanFactory().getBeanNamesForType(FunctionCatalog.class, false, false).length == 0) {
				this.context.registerBean(SimpleFunctionRegistry.class, () -> {
					List<MessageConverter> messageConverters = new ArrayList<>();
					JsonMapper jsonMapper = this.context.getBean(JsonMapper.class);

					messageConverters.add(new JsonMessageConverter(jsonMapper));
					messageConverters.add(new ByteArrayMessageConverter());
					messageConverters.add(new StringMessageConverter());
					messageConverters.add(new PrimitiveTypesFromStringMessageConverter(new DefaultConversionService()));

					SmartCompositeMessageConverter messageConverter = new SmartCompositeMessageConverter(messageConverters);

					ConversionService conversionService = new DefaultConversionService();
					return new SimpleFunctionRegistry(conversionService, messageConverter, this.context.getBean(JsonMapper.class));
				});
				this.context.registerBean(FunctionProperties.class, () -> new FunctionProperties());
				this.context.registerBean(FunctionRegistrationPostProcessor.class,
						() -> new FunctionRegistrationPostProcessor(this.context.getAutowireCapableBeanFactory()
								.getBeanProvider(FunctionRegistration.class)));
			}
		}

		private void performPreinitialization() {
			if (Boolean.getBoolean(IGNORE_BACKGROUNDPREINITIALIZER_PROPERTY_NAME)) {
				return;
			}
			try {
				Thread thread = new Thread(new Runnable() {

					@Override
					public void run() {
						runSafely(() -> new DefaultFormattingConversionService());
					}

					public void runSafely(Runnable runnable) {
						try {
							runnable.run();
						}
						catch (Throwable ex) {
							// Ignore
						}
					}

				}, "background-preinit");
				thread.start();
			}
			catch (Exception ex) {
			}
		}

		private class FunctionRegistrationPostProcessor implements BeanPostProcessor {

			@SuppressWarnings("rawtypes")
			private final ObjectProvider<FunctionRegistration> functions;

			FunctionRegistrationPostProcessor(
					@SuppressWarnings("rawtypes") ObjectProvider<FunctionRegistration> functions) {
				this.functions = functions;
			}

			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if (bean instanceof FunctionRegistry) {
					FunctionRegistry catalog = (FunctionRegistry) bean;
					for (FunctionRegistration<?> registration : this.functions) {
						Assert.notEmpty(registration.getNames(),
								"FunctionRegistration must define at least one name. Was empty");
						if (registration.getType() == null) {
							throw new IllegalStateException(
									"You need an explicit type for the function: " + registration.getNames());
							// TODO: in principle Spring could know how to extract this
							// from the supplier, but in practice there is no functional
							// bean registration with parametric types.
						}
						catalog.register(registration);
					}
				}
				return bean;
			}

		}

	}

	/**
	 * Dummy implementation of a processor.
	 */
	public static class DummyProcessor {

		public void setMetadataReaderFactory(MetadataReaderFactory obj) {
		}

	}

}
