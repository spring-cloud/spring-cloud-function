/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.cloud.function.web.function;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.WebProperties.Resources;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogInitializer;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.web.RequestProcessor;
import org.springframework.cloud.function.web.RequestProcessor.FunctionWrapper;
import org.springframework.cloud.function.web.constants.WebRequestConstants;
import org.springframework.cloud.function.web.util.FunctionWebRequestProcessingHelper;
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
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.adapter.HttpWebHandlerAdapter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.status;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 * @since 2.0
 *
 */
public class FunctionEndpointInitializer implements ApplicationContextInitializer<GenericApplicationContext> {

	private static boolean webflux = ClassUtils
			.isPresent("org.springframework.web.reactive.function.server.RouterFunction", null);

	@Override
	public void initialize(GenericApplicationContext context) {
		if (webflux && ContextFunctionCatalogInitializer.enabled
				&& context.getEnvironment().getProperty(FunctionalSpringApplication.SPRING_WEB_APPLICATION_TYPE,
						WebApplicationType.class, WebApplicationType.REACTIVE) == WebApplicationType.REACTIVE
				&& context.getEnvironment().getProperty("spring.functional.enabled", Boolean.class, false)) {
			registerEndpoint(context);
			registerWebFluxAutoConfiguration(context);
		}
	}

	private void registerWebFluxAutoConfiguration(GenericApplicationContext context) {
		context.registerBean(DefaultErrorWebExceptionHandler.class, () -> errorHandler(context));
		context.registerBean(WebHttpHandlerBuilder.WEB_HANDLER_BEAN_NAME, HttpWebHandlerAdapter.class,
				() -> httpHandler(context));
		context.addApplicationListener(new ServerListener(context));
	}

	private void registerEndpoint(GenericApplicationContext context) {
		context.registerBean(RequestProcessor.class,
				() -> new RequestProcessor(context.getBeansOfType(JsonMapper.class).values().iterator().next(),
						context.getBeanProvider(ServerCodecConfigurer.class)));
		context.registerBean(FunctionEndpointFactory.class,
				() -> new FunctionEndpointFactory(context.getBean(FunctionProperties.class), context.getBean(FunctionCatalog.class),
						context.getBean(RequestProcessor.class), context.getEnvironment()));
		RouterFunctionRegister.register(context);
	}

	private HttpWebHandlerAdapter httpHandler(GenericApplicationContext context) {
		return (HttpWebHandlerAdapter) RouterFunctions.toHttpHandler(context.getBean(RouterFunction.class),
				HandlerStrategies.empty().exceptionHandler(context.getBeansOfType(WebExceptionHandler.class).values().iterator().next())
						.codecs(config -> config.registerDefaults(true)).build());
	}

	private DefaultErrorWebExceptionHandler errorHandler(GenericApplicationContext context) {
		context.registerBean(ErrorAttributes.class, () -> new DefaultErrorAttributes());
		context.registerBean(ErrorProperties.class, () -> new ErrorProperties());

		context.registerBean(Resources.class, () -> new Resources());
		DefaultErrorWebExceptionHandler handler = new DefaultErrorWebExceptionHandler(
				context.getBeansOfType(ErrorAttributes.class).values().iterator().next(), context.getBean(Resources.class),
				context.getBean(ErrorProperties.class), context);
		ServerCodecConfigurer codecs = ServerCodecConfigurer.create();
		handler.setMessageWriters(codecs.getWriters());
		handler.setMessageReaders(codecs.getReaders());
		return handler;
	}

	private static class RouterFunctionRegister {

		private static void register(GenericApplicationContext context) {
			context.registerBean(RouterFunction.class,
					() -> context.getBean(FunctionEndpointFactory.class).functionEndpoints());
		}

	}

	private static class ServerListener implements SmartApplicationListener {

		private static Log logger = LogFactory.getLog(ServerListener.class);

		private GenericApplicationContext context;

		ServerListener(GenericApplicationContext context) {
			this.context = context;
		}

		@Override
		public void onApplicationEvent(ApplicationEvent event) {
			ApplicationContext context = ((ContextRefreshedEvent) event).getApplicationContext();
			if (context != this.context) {
				return;
			}
			if (!ClassUtils.isPresent("org.springframework.http.server.reactive.HttpHandler", null)) {
				logger.info("No web server classes found so no server to start");
				return;
			}
			Integer port = Integer.valueOf(context.getEnvironment().resolvePlaceholders("${server.port:${PORT:8080}}"));
			String address = context.getEnvironment().resolvePlaceholders("${server.address:0.0.0.0}");
			if (port >= 0) {
				HttpHandler handler = context.getBeansOfType(HttpHandler.class).values().iterator().next();
				ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(handler);
				HttpServer httpServer = HttpServer.create().host(address).port(port).handle(adapter);
				Thread thread = new Thread(
						() -> httpServer.bindUntilJavaShutdown(Duration.ofSeconds(60), this::callback),
						"server-startup");
				thread.setDaemon(false);
				thread.start();
			}
		}

		private void callback(DisposableServer server) {
			logger.info("HTTP server started on port: " + server.port());
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

	private final FunctionCatalog functionCatalog;

	private final String handler;

	private final RequestProcessor processor;

	private final FunctionProperties functionProperties;

	FunctionEndpointFactory(FunctionProperties functionProperties, FunctionCatalog functionCatalog, RequestProcessor processor, Environment environment) {
		String handler = environment.resolvePlaceholders("${function.handler}");
		if (handler.startsWith("$")) {
			handler = null;
		}
		this.processor = processor;
		this.functionCatalog = functionCatalog;
		this.handler = handler;
		this.functionProperties = functionProperties;
	}

	private FunctionInvocationWrapper extract(ServerRequest request) {
		FunctionInvocationWrapper function;
		if (handler != null) {
			logger.info("Configured function: " + handler);
			Set<String> names = this.functionCatalog.getNames(Function.class);
			Assert.isTrue(names.contains(handler), "Cannot locate function: " + handler);
			function = this.functionCatalog.lookup(Function.class, handler);
		}
		else {
			String[] accept = FunctionWebRequestProcessingHelper.acceptContentTypes(request.headers().accept());
			function = FunctionWebRequestProcessingHelper.findFunction(this.functionProperties, request.method(), functionCatalog, request.attributes(),
					request.path(), accept);
		}
		return function;
	}

	@SuppressWarnings({ "unchecked" })
	public <T> RouterFunction<?> functionEndpoints() {
		return route(POST("/**"), request -> {
			FunctionInvocationWrapper funcWrapper = extract(request);
			Class<?> outputType = funcWrapper == null ? Object.class
					: FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(funcWrapper.getOutputType()));
			FunctionWrapper wrapper = RequestProcessor.wrapper(funcWrapper);
			Mono<ResponseEntity<?>> stream = request.bodyToMono(String.class)
					.flatMap(content -> this.processor.post(wrapper, content, false));
			return stream.flatMap(entity -> {
				return status(entity.getStatusCode()).headers(headers -> headers.addAll(entity.getHeaders()))
						.body(entity.hasBody() ? Mono.just((T) entity.getBody()) : Mono.empty(), outputType);
			});
		}).andRoute(GET("/**"), request -> {
			FunctionInvocationWrapper funcWrapper = extract(request);
			Class<?> outputType = FunctionTypeUtils
					.getRawType(FunctionTypeUtils.getGenericType(funcWrapper.getOutputType()));
			if (funcWrapper.isSupplier()) {
				Object result = FunctionWebRequestProcessingHelper.invokeFunction(funcWrapper, null, funcWrapper.isInputTypeMessage());
				if (!(result instanceof Publisher)) {
					result = Mono.just(result);
				}
				return ServerResponse.ok().body(result, outputType);
			}
			else {
				FunctionWrapper wrapper = RequestProcessor.wrapper(funcWrapper);
				wrapper.headers(request.headers().asHttpHeaders());
				String argument = (String) request.attribute(WebRequestConstants.ARGUMENT).get();
				wrapper.argument(Flux.just(argument));
				Object result = FunctionWebRequestProcessingHelper.invokeFunction(funcWrapper, wrapper.argument(),
						funcWrapper.isInputTypeMessage());
				return ServerResponse.ok().body(result, outputType);
			}
		});
	}

}
