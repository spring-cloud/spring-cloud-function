/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.function.web.function;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.web.BasicStringConverter;
import org.springframework.cloud.function.web.RequestProcessor;
import org.springframework.cloud.function.web.RequestProcessor.FunctionWrapper;
import org.springframework.cloud.function.web.StringConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.adapter.HttpWebHandlerAdapter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.status;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 *
 * @author Dave Syer
 * @since 2.0
 *
 */
class FunctionEndpointInitializer
		implements ApplicationContextInitializer<GenericApplicationContext> {

	@Override
	public void initialize(GenericApplicationContext context) {
		if (context.getEnvironment().getProperty(
				FunctionalSpringApplication.SPRING_WEB_APPLICATION_TYPE,
				WebApplicationType.class,
				WebApplicationType.REACTIVE) == WebApplicationType.REACTIVE
				&& context.getEnvironment().getProperty("spring.functional.enabled",
						Boolean.class, false)
				&& ClassUtils.isPresent(
						"org.springframework.http.server.reactive.HttpHandler", null)) {
			registerEndpoint(context);
			registerWebFluxAutoConfiguration(context);
		}
	}

	private void registerWebFluxAutoConfiguration(GenericApplicationContext context) {
		context.registerBean(DefaultErrorWebExceptionHandler.class,
				() -> errorHandler(context));
		context.registerBean(WebHttpHandlerBuilder.WEB_HANDLER_BEAN_NAME,
				HttpWebHandlerAdapter.class, () -> httpHandler(context));
		context.addApplicationListener(new ServerListener(context));
	}

	private void registerEndpoint(GenericApplicationContext context) {
		context.registerBean(StringConverter.class,
				() -> new BasicStringConverter(context.getBean(FunctionInspector.class),
						context.getBeanFactory()));
		context.registerBean(RequestProcessor.class,
				() -> new RequestProcessor(context.getBean(FunctionInspector.class),
						context.getBeanProvider(JsonMapper.class),
						context.getBean(StringConverter.class),
						context.getBeanProvider(ServerCodecConfigurer.class)));
		context.registerBean(FunctionEndpointFactory.class,
				() -> new FunctionEndpointFactory(context.getBean(FunctionCatalog.class),
						context.getBean(FunctionInspector.class),
						context.getBean(RequestProcessor.class),
						context.getEnvironment()));
		context.registerBean(RouterFunction.class,
				() -> context.getBean(FunctionEndpointFactory.class).functionEndpoints());
	}

	private HttpWebHandlerAdapter httpHandler(GenericApplicationContext context) {
		return (HttpWebHandlerAdapter) RouterFunctions.toHttpHandler(
				context.getBean(RouterFunction.class),
				HandlerStrategies.empty()
						.exceptionHandler(context.getBean(WebExceptionHandler.class))
						.codecs(config -> config.registerDefaults(true)).build());
	}

	private DefaultErrorWebExceptionHandler errorHandler(
			GenericApplicationContext context) {
		context.registerBean(ErrorAttributes.class, () -> new DefaultErrorAttributes());
		context.registerBean(ErrorProperties.class, () -> new ErrorProperties());
		context.registerBean(ResourceProperties.class, () -> new ResourceProperties());
		DefaultErrorWebExceptionHandler handler = new DefaultErrorWebExceptionHandler(
				context.getBean(ErrorAttributes.class),
				context.getBean(ResourceProperties.class),
				context.getBean(ErrorProperties.class), context);
		ServerCodecConfigurer codecs = ServerCodecConfigurer.create();
		handler.setMessageWriters(codecs.getWriters());
		handler.setMessageReaders(codecs.getReaders());
		return handler;
	}

	private static class ServerListener implements SmartApplicationListener {

		private static Log logger = LogFactory.getLog(ServerListener.class);

		private GenericApplicationContext context;

		public ServerListener(GenericApplicationContext context) {
			this.context = context;
		}

		@Override
		public void onApplicationEvent(ApplicationEvent event) {
			ApplicationContext context = ((ContextRefreshedEvent) event)
					.getApplicationContext();
			if (context != this.context) {
				return;
			}
			if (!ClassUtils.isPresent(
					"org.springframework.http.server.reactive.HttpHandler", null)) {
				logger.info("No web server classes found so no server to start");
				return;
			}
			Integer port = Integer.valueOf(context.getEnvironment()
					.resolvePlaceholders("${server.port:${PORT:8080}}"));
			String address = context.getEnvironment()
					.resolvePlaceholders("${server.address:0.0.0.0}");
			if (port >= 0) {
				HttpHandler handler = context.getBean(HttpHandler.class);
				ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(
						handler);
				HttpServer httpServer = HttpServer.create().host(address).port(port)
						.handle(adapter);
				Thread thread = new Thread(() -> httpServer
						.bindUntilJavaShutdown(Duration.ofSeconds(60), this::callback),
						"server-startup");
				thread.setDaemon(false);
				thread.start();
			}
		}

		private void callback(DisposableServer server) {
			logger.info("Server started");
			try {
				double uptime = ManagementFactory.getRuntimeMXBean().getUptime();
				logger.info("JVM running for " + uptime + "ms");
			}
			catch (Throwable e) {
				// ignore
			}
		}

		@Override
		public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
			return eventType.isAssignableFrom(ContextRefreshedEvent.class);
		}

	}

}

class FunctionEndpointFactory {

	private static Log logger = LogFactory.getLog(FunctionEndpointFactory.class);

	private Function<Flux<?>, Flux<?>> function;

	private FunctionInspector inspector;

	private RequestProcessor processor;

	public FunctionEndpointFactory(FunctionCatalog catalog, FunctionInspector inspector,
			RequestProcessor processor, Environment environment) {
		String handler = environment.resolvePlaceholders("${function.handler}");
		if (handler.startsWith("$")) {
			handler = null;
		}
		this.processor = processor;
		this.inspector = inspector;
		this.function = extract(catalog, handler);
	}

	private Function<Flux<?>, Flux<?>> extract(FunctionCatalog catalog, String handler) {
		Set<String> names = catalog.getNames(Function.class);
		if (!names.isEmpty()) {
			logger.info("Found functions: " + names);
			if (handler != null) {
				logger.info("Configured function: " + handler);
				Assert.isTrue(names.contains(handler),
						"Cannot locate function: " + handler);
				return catalog.lookup(Function.class, handler);
			}
			return catalog.lookup(Function.class, names.iterator().next());
		}
		throw new IllegalStateException("No function defined");
	}

	@SuppressWarnings({ "unchecked" })
	public <T> RouterFunction<?> functionEndpoints() {
		return route(POST("/"), request -> {
			Class<T> outputType = (Class<T>) this.inspector.getOutputType(this.function);
			FunctionWrapper wrapper = RequestProcessor.wrapper(function, null, null);
			Mono<ResponseEntity<?>> stream = request.bodyToMono(String.class)
					.flatMap(content -> processor.post(wrapper, content, false));
			return stream.flatMap(entity -> status(entity.getStatusCode())
					.headers(headers -> headers.addAll(entity.getHeaders()))
					.body(Mono.just((T) entity.getBody()), outputType));
		});
	}

}