/*
 * Copyright 2018-2019 the original author or authors.
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

import java.time.Duration;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * A {@link Supplier} that pulls data from an HTTP endpoint. Repeatedly polls the endpoint
 * until a non-2xx response is received, at which point it will repeatedly produced a
 * Mono at 1 sec intervals until the next 2xx response.
 *
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class HttpSupplier implements Supplier<Flux<?>> {

	private static Log logger = LogFactory.getLog(HttpSupplier.class);

	private WebClient client;

	private ExporterProperties props;

	/**
	 * @param client the WebClient to use. The baseUrl should be set.
	 * @param props the ExporterProperties to use to parameterize the requests.
	 */
	public HttpSupplier(WebClient client, ExporterProperties props) {
		this.client = client;
		this.props = props;
	}

	@Override
	public Flux<?> get() {
		return get(this.client);
	}

	private Flux<?> get(WebClient client) {
		Flux<?> result = client.get().uri(this.props.getSource().getUrl()).exchange()
				.flatMap(this::transform).repeat();
		if (this.props.isDebug()) {
			result = result.log();
		}
		return result.onErrorResume(TerminateException.class, error -> Mono.empty());
	}

	private Mono<?> transform(ClientResponse response) {
		HttpStatus status = response.statusCode();
		if (!status.is2xxSuccessful()) {
			if (this.props.isDebug()) {
				logger.info("Delaying supplier based on status=" + response.statusCode());
			}
			return Mono.delay(Duration.ofSeconds(1));
		}
		return response.bodyToMono(this.props.getSource().getType())
				.map(value -> message(response, value));
	}

	private Object message(ClientResponse response, Object payload) {
		if (!this.props.getSource().isIncludeHeaders()) {
			return payload;
		}
		return MessageBuilder.withPayload(payload)
				.copyHeaders(HeaderUtils.fromHttp(
						HeaderUtils.sanitize(response.headers().asHttpHeaders())))
				.setHeader("scf-sink-url", this.props.getSink().getUrl())
				.setHeader("scf-func-name", this.props.getSink().getName())
				.build();
	}

	@SuppressWarnings("serial")
	private static class TerminateException extends RuntimeException {

		@SuppressWarnings("unused")
		TerminateException() {
			super("Planned termination");
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}

	}
}
