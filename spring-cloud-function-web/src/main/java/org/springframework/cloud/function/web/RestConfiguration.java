/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.web;

import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.BodyExtractors.toFlux;
import static org.springframework.web.reactive.function.BodyInserters.fromPublisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.NettyContext;
import reactor.ipc.netty.http.server.HttpServer;

/**
 * @author Mark Fisher
 */
@Configuration
@EnableConfigurationProperties({ WebConfigurationProperties.class })
@ConditionalOnClass({ HttpHandler.class, NettyContext.class })
public class RestConfiguration {

	@Autowired
	private WebConfigurationProperties webProperties;

	@Bean
	public HttpHandler httpHandler(FunctionCatalog registry) {
		FunctionInvokingHandler handler = new FunctionInvokingHandler(registry);
		RouterFunction<?> route = RouterFunctions
				.route(RequestPredicates.POST(webProperties.getPath() + "/{name}")
						.and(RequestPredicates.contentType(MediaType.TEXT_PLAIN)),
						handler::handlePost)
				.andRoute(RequestPredicates.GET(webProperties.getPath() + "/{name}"),
						handler::handleGet);
		return RouterFunctions.toHttpHandler(route);
	}

	@Bean
	public LifecycleAwareHttpServer httpServer(HttpHandler handler) {
		return new LifecycleAwareHttpServer(handler, webProperties.getPort());
	}

	private static class FunctionInvokingHandler {

		private final FunctionCatalog registry;

		private FunctionInvokingHandler(FunctionCatalog registry) {
			this.registry = registry;
		}

		private Mono<ServerResponse> handlePost(ServerRequest request) {
			Flux<String> input = request.body(toFlux(String.class));
			String name = request.pathVariable("name");
			Function<Flux<String>, Flux<String>> function = (name.indexOf(',') == -1)
					? registry.lookupFunction(name)
					: registry.composeFunction(
							StringUtils.commaDelimitedListToStringArray(name));
			Publisher<String> output = function.apply(input);
			return ServerResponse.ok().body(fromPublisher(output, String.class));
		}

		private Mono<ServerResponse> handleGet(ServerRequest request) {
			String name = request.pathVariable("name");
			Supplier<Flux<String>> function = registry.lookupSupplier(name);
			Publisher<String> output = function.get();
			return ServerResponse.ok().body(fromPublisher(output, String.class));
		}
	}

	private static class LifecycleAwareHttpServer
			implements InitializingBean, DisposableBean {

		private final HttpHandler handler;

		private final int port;

		private volatile NettyContext server;

		private LifecycleAwareHttpServer(HttpHandler handler, int port) {
			this.handler = handler;
			this.port = port;
		}

		@Override
		public void afterPropertiesSet() {
			ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(
					this.handler);
			final HttpServer server = HttpServer.create("localhost", port);
			Thread thread = new Thread(new Runnable() {

				@Override
				public void run() {
					LifecycleAwareHttpServer.this.server = server.newHandler(adapter)
							.block();
					while (LifecycleAwareHttpServer.this.server != null) {
						try {
							Thread.sleep(100L);
						}
						catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}
				}
			});
			thread.setDaemon(false);
			thread.start();
		}

		@Override
		public void destroy() {
			if (this.server != null) {
				this.server.dispose();
				this.server = null;
			}
		}
	}
}
