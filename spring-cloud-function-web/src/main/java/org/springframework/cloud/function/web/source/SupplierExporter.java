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

package org.springframework.cloud.function.web.source;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Forwards items obtained from a {@link Supplier} or set of suppliers to an external HTTP
 * endpoint.
 *
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 */
public class SupplierExporter implements SmartLifecycle {

	private static Log logger = LogFactory.getLog(SupplierExporter.class);

	private final FunctionCatalog catalog;

	private final WebClient client;

	private final DestinationResolver destinationResolver;

	private final RequestBuilder requestBuilder;

	private final String supplier;

	private final String contentType;

	private volatile boolean running;

	private volatile boolean ok = true;

	private boolean autoStartup = true;

	private boolean debug = true;

	private volatile Disposable subscription;

	SupplierExporter(RequestBuilder requestBuilder,
			DestinationResolver destinationResolver, FunctionCatalog catalog,
			WebClient client, ExporterProperties exporterProperties) {
		this.requestBuilder = requestBuilder;
		this.destinationResolver = destinationResolver;
		this.catalog = catalog;
		this.client = client;
		this.debug = exporterProperties.isDebug();
		this.autoStartup = exporterProperties.isAutoStartup();
		this.supplier = exporterProperties.getSink().getName();
		this.contentType = exporterProperties.getSink().getContentType();
	}

	@Override
	public void start() {
		if (this.running) {
			return;
		}
		logger.info("Starting");

		Flux<Object> streams = Flux.empty();
		Set<String> names = this.supplier == null ? this.catalog.getNames(Supplier.class)
				: Collections.singleton(this.supplier);

		boolean suppliersPresent = false;
		for (String name : names) {
			Supplier<Publisher<Object>> supplier = this.catalog.lookup(name, this.contentType);
			if (supplier == null) {
				logger.warn("No such Supplier: " + name);
				continue;
			}
			streams = streams.mergeWith(forward(supplier, name));
			suppliersPresent = true;
		}
		if (suppliersPresent) {
			this.subscription = streams
					.retryWhen(Retry.backoff(5, Duration.ofSeconds(1)))
//					.retry(error -> {
//						/*
//						 * The ConnectException may happen if a server is not yet available/reachable
//						 * The ClassCast is to handle delayed Mono issued by HttpSupplier.transform for non-2xx responses
//						 */
//						boolean retry = error instanceof ConnectException || error instanceof ClassCastException
//								&& this.running;
//						if (!retry) {
//							this.ok = false;
//							if (!this.debug) {
//								logger.info(error);
//							}
//							stop();
//						}
//						return retry;
//					}
//					)
					.doOnComplete(() -> {
						stop();
					})
					.subscribe();

			this.ok = true;
			this.running = true;
		}
	}

	public boolean isOk() {
		return this.ok;
	}

	@Override
	public void stop() {
		logger.info("Stopping");
		this.running = false;
		this.subscription.dispose();
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	@Override
	public int getPhase() {
		return 0;
	}

	@Override
	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	@Override
	public void stop(Runnable callback) {
		stop();
		callback.run();
	}

	private Flux<ClientResponse> forward(Supplier<Publisher<Object>> supplier, String name) {
		Flux o = (Flux) supplier.get();
//		o.subscribe(v -> {
//			System.out.println(v);
//		});
		return Flux.from(o).flatMap(value -> {
			String destination = this.destinationResolver.destination(supplier, name, value);
			if (this.debug) {
				logger.info("Posting to: " + destination);
			}
			return post(uri(destination), destination, value);
		});
	}

	private Mono<ClientResponse> post(URI uri, String destination, Object value) {
		Object body = value;
		if (value instanceof Message) {
			Message<?> message = (Message<?>) value;
			body = message.getPayload();
		}
		if (this.debug) {
			logger.debug("Sending BODY as type: " + body.getClass().getName());
		}
		Mono<ClientResponse> result = this.client.post().uri(uri)
				.headers(headers -> headers(headers, destination, value)).bodyValue(body)
				.exchange()
				.doOnNext(response -> {
					if (this.debug) {
						logger.debug("Response STATUS: " + response.statusCode());
					}
				});
		if (this.debug) {
			result = result.log();
		}
		return result;
	}

	private void headers(HttpHeaders headers, String destination, Object value) {
		headers.putAll(this.requestBuilder.headers(destination, value));
	}

	private URI uri(String destination) {
		return this.requestBuilder.uri(destination);
	}
}
