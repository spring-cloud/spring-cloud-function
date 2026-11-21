/*
 * Copyright 2026-present the original author or authors.
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
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the lifecycle of {@link SupplierExporter}.
 */
public class SupplierExporterTests {

	@Test
	void startAndStopTogglesRunningState() {
		SupplierExporter exporter = exporterWithNeverEndingSupplier();

		assertThat(exporter.isRunning()).isFalse();
		exporter.start();
		assertThat(exporter.isRunning()).isTrue();
		assertThat(exporter.isOk()).isTrue();

		exporter.stop();
		assertThat(exporter.isRunning()).isFalse();
	}

	@Test
	void startIsIdempotentWhileRunning() {
		SupplierExporter exporter = exporterWithNeverEndingSupplier();

		exporter.start();
		exporter.start();
		assertThat(exporter.isRunning()).isTrue();

		exporter.stop();
		assertThat(exporter.isRunning()).isFalse();
	}

	@Test
	void stopWithCallbackStopsAndInvokesCallback() {
		SupplierExporter exporter = exporterWithNeverEndingSupplier();
		boolean[] called = new boolean[1];

		exporter.start();
		exporter.stop(() -> called[0] = true);

		assertThat(exporter.isRunning()).isFalse();
		assertThat(called[0]).isTrue();
	}

	@SuppressWarnings("unchecked")
	private static SupplierExporter exporterWithNeverEndingSupplier() {
		Supplier<Flux<Object>> supplier = Flux::never;
		FunctionCatalog catalog = mock(FunctionCatalog.class);
		when(catalog.lookup(eq("words"), any(String[].class))).thenReturn(supplier);

		ExporterProperties properties = new ExporterProperties();
		properties.getSink().setName("words");
		properties.setDebug(false);

		RequestBuilder requestBuilder = new RequestBuilder() {
			@Override
			public URI uri(String destination) {
				return URI.create("http://localhost/" + destination);
			}

			@Override
			public HttpHeaders headers(String destination, Object value) {
				return new HttpHeaders();
			}
		};
		DestinationResolver resolver = (s, name, value) -> name;
		return new SupplierExporter(requestBuilder, resolver, catalog, WebClient.create(), properties);
	}

}
