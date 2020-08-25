/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.function.rsocket;

import java.util.function.Consumer;
import java.util.function.Function;

import io.rsocket.routing.client.spring.RoutingMetadata;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.SocketUtils;


/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class RoutingBrokerTests {

	@Test
	public void testImperativeFunctionAsRequestReply() throws Exception {

		ConfigurableApplicationContext functionContext = null;
		ConfigurableApplicationContext brokerContext = null;
		ConfigurableApplicationContext clientContext = null;
		try {
			int routingBrokerProxyPort = SocketUtils.findAvailableTcpPort();
			int routingBrokerClusterPort = SocketUtils.findAvailableTcpPort();

			// start broker
			brokerContext = new SpringApplicationBuilder(SimpleConfiguration.class).web(WebApplicationType.NONE).run(
				"--logging.level.io.rsocket.routing.broker=TRACE",
				"--spring.cloud.function.rsocket.enabled=false",
				"--io.rsocket.routing.client.enabled=false",
				"--io.rsocket.routing.broker.enabled=true",
				"--io.rsocket.routing.broker.tcp.port=" + routingBrokerProxyPort,
				"--io.rsocket.routing.broker.cluster.port=" + routingBrokerClusterPort);

			// start function connecting to broker, service-name=samplefn
			functionContext = new SpringApplicationBuilder(SampleFunctionConfiguration.class)
				.web(WebApplicationType.NONE).run(
					"--logging.level.org.springframework.cloud.function=DEBUG",
					"--io.rsocket.routing.client.enabled=true",
					"--io.rsocket.routing.client.service-name=samplefn",
					"--io.rsocket.routing.client.brokers[0].host=localhost",
					"--io.rsocket.routing.client.brokers[0].port=" + routingBrokerProxyPort,
					"--io.rsocket.routing.broker.enabled=false",
					"--spring.cloud.function.definition=uppercase");

			// start testclient connecting to broker, for RSocketRequester
			clientContext = new SpringApplicationBuilder(SimpleConfiguration.class)
				.web(WebApplicationType.NONE).run(
					"--logging.level.io.rsocket.routing.client=TRACE",
					"--spring.cloud.function.rsocket.enabled=false",
					"--io.rsocket.routing.client.enabled=true",
					"--io.rsocket.routing.client.service-name=testclient",
					"--io.rsocket.routing.client.address.toupper.service_name=samplefn",
					"--io.rsocket.routing.client.brokers[0].host=localhost",
					"--io.rsocket.routing.client.brokers[0].port=" + routingBrokerProxyPort,
					"--io.rsocket.routing.broker.enabled=false");

			RSocketRequester requester = clientContext.getBean(RSocketRequester.class);
			//RoutingMetadata metadata = clientContext.getBean(RoutingMetadata.class);
			Mono<String> result = requester.route("toupper") // used to find a messagemapping, so unused here
				// auto creates metadata
				//.metadata(metadata.address("samplefn"))
				.data("\"hello\"")
				.retrieveMono(String.class);

			StepVerifier
				.create(result)
				.expectNext("\"HELLO\"")
				.expectComplete()
				.verify();
		} finally {
			if (functionContext != null) {
				functionContext.close();
			}
			if (brokerContext != null) {
				brokerContext.close();
			}
			if (clientContext != null) {
				clientContext.close();
			}
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class SimpleConfiguration {

	}

	@EnableAutoConfiguration
	@Configuration
	public static class SampleFunctionConfiguration {
		@Bean
		public Function<String, String> uppercase() {
			return v -> {
				return v.toUpperCase();
			};
		}

		@Bean
		public Function<String, String> concat() {
			return v -> {
				return v + v;
			};
		}

		@Bean
		public Function<String, String> echo() {
			return v -> v;
		}

		@Bean
		public Function<Flux<String>, Flux<String>> uppercaseReactive() {
			return flux -> flux.map(v -> {
				System.out.println("Uppercasing: " + v);
				return v.toUpperCase();
			});
		}

		@Bean
		public Consumer<byte[]> log() {
			return v -> {
				System.out.println("==> In Consumer: " + new String(v));
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class AdditionalFunctionConfiguration {
		@Bean
		public Function<String, String> reverse() {
			return v -> {
				return new StringBuilder(v).reverse().toString();
			};
		}

		@Bean
		public Function<String, String> wrap() {
			return v -> {
				return "(" + v + ")";
			};
		}
	}
}
