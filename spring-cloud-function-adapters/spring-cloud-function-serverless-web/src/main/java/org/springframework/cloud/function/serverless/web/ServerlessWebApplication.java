/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.function.serverless.web;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aot.AotDetector;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.DefaultPropertiesPropertySource;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.ResourceBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiStyle;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.util.Assert;
import org.springframework.web.context.ConfigurableWebApplicationContext;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class ServerlessWebApplication extends SpringApplication {

	private static final Log logger = LogFactory.getLog(ServerlessWebApplication.class);

	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	private ApplicationContextFactory applicationContextFactory = ApplicationContextFactory.DEFAULT;

	private boolean allowCircularReferences;

	private boolean allowBeanDefinitionOverriding;

	private boolean logStartupInfo = true;

	private boolean lazyInitialization = false;

	private WebApplicationType webApplicationType;

	private List<ApplicationContextInitializer<?>> initializers;

	public static ConfigurableWebApplicationContext run(Class<?>[] primarySources, String[] args) {
		return new ServerlessWebApplication(primarySources).run(args);
	}

	ServerlessWebApplication(Class<?>... classes) {
		super(classes);
	}

	@Override
	public ConfigurableWebApplicationContext run(String... args) {
		this.webApplicationType = WebApplicationType.SERVLET;
		DefaultBootstrapContext bootstrapContext = createBootstrapContext();
		ConfigurableWebApplicationContext context = null;
		SpringApplicationRunListeners listeners = getRunListeners(args);
		listeners.starting(bootstrapContext, this.getMainApplicationClass());
		try {
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
			ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
			Banner printedBanner = printBanner(environment);
			context = (ConfigurableWebApplicationContext) createApplicationContext();
			context.setApplicationStartup(this.applicationStartup);
			prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
		}
		catch (Throwable ex) {
			throw new IllegalStateException(ex);
		}

		//throw new AbandonedRunException();
		return context;
	}


	private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
			DefaultBootstrapContext bootstrapContext, ApplicationArguments applicationArguments) {
		// Create and configure the environment
		ConfigurableEnvironment environment = getOrCreateEnvironment();
		configureEnvironment(environment, applicationArguments.getSourceArgs());
		ConfigurationPropertySources.attach(environment);
		listeners.environmentPrepared(bootstrapContext, environment);
		DefaultPropertiesPropertySource.moveToEnd(environment);
		Assert.state(!environment.containsProperty("spring.main.environment-prefix"),
				"Environment prefix cannot be set via properties.");
		bindToSpringApplication(environment);
		ConfigurationPropertySources.attach(environment);
		return environment;
	}

	private ConfigurableEnvironment getOrCreateEnvironment() {
		ConfigurableEnvironment environment = this.applicationContextFactory.createEnvironment(this.webApplicationType);
		if (environment == null && this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
			environment = ApplicationContextFactory.DEFAULT.createEnvironment(this.webApplicationType);
		}
		return environment;
	}

	private SpringApplicationRunListeners getRunListeners(String[] args) {
		ArgumentResolver argumentResolver = ArgumentResolver.of(SpringApplication.class, this);
		argumentResolver = argumentResolver.and(String[].class, args);
		List<SpringApplicationRunListener> listeners = getSpringFactoriesInstances(SpringApplicationRunListener.class, argumentResolver);
		return new SpringApplicationRunListeners(logger, listeners, this.applicationStartup);
	}

	private Banner printBanner(ConfigurableEnvironment environment) {
		ResourceLoader resourceLoader = (this.getResourceLoader() != null) ? this.getResourceLoader()
				: new DefaultResourceLoader(null);
		Banner.Mode  bannerMode = environment.containsProperty("spring.main.banner-mode")
				? Banner.Mode.valueOf(environment.getProperty("spring.main.banner-mode").trim().toUpperCase(Locale.ROOT))
						: Banner.Mode.CONSOLE;

		if (bannerMode == Banner.Mode.OFF) {
			return null;
		}
		SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, new SpringAwsBanner());
		return bannerPrinter.print(environment, this.getMainApplicationClass(), System.out);
	}


	private DefaultBootstrapContext createBootstrapContext() {
		DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
		ArrayList<BootstrapRegistryInitializer> bootstrapRegistryInitializers = new ArrayList<>(getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
		bootstrapRegistryInitializers.forEach((initializer) -> initializer.initialize(bootstrapContext));
		return bootstrapContext;
	}

	private <T> List<T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, null);
	}

	private <T> List<T> getSpringFactoriesInstances(Class<T> type, ArgumentResolver argumentResolver) {
		return SpringFactoriesLoader.forDefaultResourceLocation(getClassLoader()).load(type, argumentResolver);
	}

	private void prepareContext(DefaultBootstrapContext bootstrapContext, ConfigurableApplicationContext context,
			ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments, Banner printedBanner) {
		context.setEnvironment(environment);
		postProcessApplicationContext(context);
		addAotGeneratedInitializerIfNecessary(this.initializers);
		applyInitializers(context);
		listeners.contextPrepared(context);
		bootstrapContext.close(context);
		if (this.logStartupInfo) {
			logStartupInfo(context.getParent() == null);
			logStartupProfileInfo(context);
		}
		// Add boot specific singleton beans
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
		if (printedBanner != null) {
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
		if (beanFactory instanceof AbstractAutowireCapableBeanFactory autowireCapableBeanFactory) {
			autowireCapableBeanFactory.setAllowCircularReferences(this.allowCircularReferences);
			if (beanFactory instanceof DefaultListableBeanFactory listableBeanFactory) {
				listableBeanFactory.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
			}
		}
		if (this.lazyInitialization) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}
		context.addBeanFactoryPostProcessor(new PropertySourceOrderingBeanFactoryPostProcessor(context));
		if (!AotDetector.useGeneratedArtifacts()) {
			// Load the sources
			Set<Object> sources = getAllSources();
			Assert.notEmpty(sources, "Sources must not be empty");
			load(context, sources.toArray(new Object[0]));
		}
		listeners.contextLoaded(context);
	}

	private void addAotGeneratedInitializerIfNecessary(List<ApplicationContextInitializer<?>> initializers) {
		if (AotDetector.useGeneratedArtifacts()) {
			List<ApplicationContextInitializer<?>> aotInitializers = new ArrayList<>(
					initializers.stream().filter(AotApplicationContextInitializer.class::isInstance).toList());
			if (aotInitializers.isEmpty()) {
				String initializerClassName = this.getMainApplicationClass().getName() + "__ApplicationContextInitializer";
				aotInitializers.add(AotApplicationContextInitializer.forInitializerClasses(initializerClassName));
			}
			initializers.removeAll(aotInitializers);
			initializers.addAll(0, aotInitializers);
		}
	}

	private static class PropertySourceOrderingBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

		private final ConfigurableApplicationContext context;

		PropertySourceOrderingBeanFactoryPostProcessor(ConfigurableApplicationContext context) {
			this.context = context;
		}

		@Override
		public int getOrder() {
			return Ordered.HIGHEST_PRECEDENCE;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			DefaultPropertiesPropertySource.moveToEnd(this.context.getEnvironment());
		}

	}

	private static class SpringApplicationBannerPrinter {

		static final String BANNER_LOCATION_PROPERTY = "spring.banner.location";

		static final String DEFAULT_BANNER_LOCATION = "banner.txt";

		private static final Banner DEFAULT_BANNER = new SpringAwsBanner();

		private final ResourceLoader resourceLoader;

		private final Banner fallbackBanner;

		SpringApplicationBannerPrinter(ResourceLoader resourceLoader, Banner fallbackBanner) {
			this.resourceLoader = resourceLoader;
			this.fallbackBanner = fallbackBanner;
		}

		Banner print(Environment environment, Class<?> sourceClass, PrintStream out) {
			Banner banner = getBanner(environment);
			banner.printBanner(environment, sourceClass, out);
			return new PrintedBanner(banner, sourceClass);
		}

		private Banner getBanner(Environment environment) {
			Banner textBanner = getTextBanner(environment);
			if (textBanner != null) {
				return textBanner;
			}
			if (this.fallbackBanner != null) {
				return this.fallbackBanner;
			}
			return DEFAULT_BANNER;
		}

		private Banner getTextBanner(Environment environment) {
			String location = environment.getProperty(BANNER_LOCATION_PROPERTY, DEFAULT_BANNER_LOCATION);
			Resource resource = this.resourceLoader.getResource(location);
			try {
				if (resource.exists() && !resource.getURL().toExternalForm().contains("liquibase-core")) {
					return new ResourceBanner(resource);
				}
			}
			catch (IOException ex) {
				// Ignore
			}
			return null;
		}

		/**
		 * Decorator that allows a {@link Banner} to be printed again without needing to
		 * specify the source class.
		 */
		private static class PrintedBanner implements Banner {

			private final Banner banner;

			private final Class<?> sourceClass;

			PrintedBanner(Banner banner, Class<?> sourceClass) {
				this.banner = banner;
				this.sourceClass = sourceClass;
			}

			@Override
			public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
				sourceClass = (sourceClass != null) ? sourceClass : this.sourceClass;
				this.banner.printBanner(environment, sourceClass, out);
			}

		}
	}

	private static class SpringAwsBanner implements Banner {

		private static final String[] BANNER = { "", "\n"
				+ "  ____             _                _____        ______    _                    _         _       \n"
				+ " / ___| _ __  _ __(_)_ __   __ _   / / \\ \\      / / ___|  | |    __ _ _ __ ___ | |__   __| | __ _ \n"
				+ " \\___ \\| '_ \\| '__| | '_ \\ / _` | / / _ \\ \\ /\\ / /\\___ \\  | |   / _` | '_ ` _ \\| '_ \\ / _` |/ _` |\n"
				+ "  ___) | |_) | |  | | | | | (_| |/ / ___ \\ V  V /  ___) | | |__| (_| | | | | | | |_) | (_| | (_| |\n"
				+ " |____/| .__/|_|  |_|_| |_|\\__, /_/_/   \\_\\_/\\_/  |____/  |_____\\__,_|_| |_| |_|_.__/ \\__,_|\\__,_|\n"
				+ "       |_|                 |___/                                                                  \n"
				+ "" };

		private static final String SPRING_BOOT = " :: Spring Boot :: ";

		private static final int STRAP_LINE_SIZE = 42;

		@Override
		public void printBanner(Environment environment, Class<?> sourceClass, PrintStream printStream) {
			for (String line : BANNER) {
				printStream.println(line);
			}
			String version = SpringBootVersion.getVersion();
			version = (version != null) ? " (v" + version + ")" : "";
			StringBuilder padding = new StringBuilder();
			while (padding.length() < STRAP_LINE_SIZE - (version.length() + SPRING_BOOT.length())) {
				padding.append(" ");
			}

			printStream.println(AnsiOutput.toString(AnsiColor.GREEN, SPRING_BOOT, AnsiColor.DEFAULT, padding.toString(),
					AnsiStyle.FAINT, version));
			printStream.println();
		}

	}

	private static class SpringApplicationRunListeners {

		private final List<SpringApplicationRunListener> listeners;

		private final ApplicationStartup applicationStartup;

		SpringApplicationRunListeners(Log log, List<SpringApplicationRunListener> listeners,
				ApplicationStartup applicationStartup) {
			this.listeners = List.copyOf(listeners);
			this.applicationStartup = applicationStartup;
		}

		void starting(ConfigurableBootstrapContext bootstrapContext, Class<?> mainApplicationClass) {
			doWithListeners("spring.boot.application.starting", (listener) -> listener.starting(bootstrapContext),
					(step) -> {
						if (mainApplicationClass != null) {
							step.tag("mainApplicationClass", mainApplicationClass.getName());
						}
					});
		}

		void environmentPrepared(ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
			doWithListeners("spring.boot.application.environment-prepared",
					(listener) -> listener.environmentPrepared(bootstrapContext, environment));
		}

		void contextPrepared(ConfigurableApplicationContext context) {
			doWithListeners("spring.boot.application.context-prepared", (listener) -> listener.contextPrepared(context));
		}

		void contextLoaded(ConfigurableApplicationContext context) {
			doWithListeners("spring.boot.application.context-loaded", (listener) -> listener.contextLoaded(context));
		}
		private void doWithListeners(String stepName, Consumer<SpringApplicationRunListener> listenerAction) {
			doWithListeners(stepName, listenerAction, null);
		}

		private void doWithListeners(String stepName, Consumer<SpringApplicationRunListener> listenerAction,
				Consumer<StartupStep> stepAction) {
			StartupStep step = this.applicationStartup.start(stepName);
			this.listeners.forEach(listenerAction);
			if (stepAction != null) {
				stepAction.accept(step);
			}
			step.end();
		}
	}
}
