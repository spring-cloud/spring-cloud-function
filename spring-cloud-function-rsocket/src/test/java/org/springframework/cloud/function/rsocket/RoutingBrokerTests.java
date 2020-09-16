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

import java.util.function.Function;

import io.rsocket.routing.client.spring.RoutingMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
 * @author Spencer Gibb
 * @author Oleg Zhurakousky
 * @since 3.1
 */
@Disabled
public class RoutingBrokerTests {

	ConfigurableApplicationContext functionContext;
	ConfigurableApplicationContext brokerContext;
	ConfigurableApplicationContext clientContext;

	@AfterEach
	public void cleanup() {
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

	@Test
	public void testRoutingWithProperty() throws Exception {
		this.setup(true);
		RSocketRequester requester = clientContext.getBean(RSocketRequester.class);
		// route(uppercase) used to find function, must match io.rsocket.routing.client.address entry
		Mono<String> result = requester.route("uppercase")
			// auto creates metadata
			.data("\"hello\"")
			.retrieveMono(String.class);

		StepVerifier
			.create(result)
			.expectNext("HELLO")
			.expectComplete()
			.verify();
	}

	@Test
	public void testRoutingWithMessage() throws Exception {
		this.setup(false);
		RSocketRequester requester = clientContext.getBean(RSocketRequester.class);
		RoutingMetadata metadata = clientContext.getBean(RoutingMetadata.class);
		Mono<String> result = requester.route("uppercase") // used to find function
			.metadata(metadata.address("samplefn"))
			.data("\"hello\"")
			.retrieveMono(String.class);

		StepVerifier
			.create(result)
			.expectNext("HELLO")
			.expectComplete()
			.verify();
	}

	private void setup(boolean routingWithProperty) {
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
		functionContext = new SpringApplicationBuilder(SampleFunctionConfiguration.class).web(WebApplicationType.NONE)
				.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--io.rsocket.routing.client.enabled=true",
						"--io.rsocket.routing.client.service-name=samplefn",
						"--io.rsocket.routing.client.brokers[0].tcp.host=localhost",
						"--io.rsocket.routing.client.brokers[0].tcp.port=" + routingBrokerProxyPort,
						"--io.rsocket.routing.broker.enabled=false",
						"--spring.cloud.function.definition=uppercase");

		// start testclient connecting to broker, for RSocketRequester
		clientContext = new SpringApplicationBuilder(SimpleConfiguration.class).web(WebApplicationType.NONE).run(
				"--logging.level.io.rsocket.routing.client=TRACE",
				"--spring.cloud.function.rsocket.enabled=false",
				"--io.rsocket.routing.client.enabled=true",
				"--io.rsocket.routing.client.service-name=testclient",
				routingWithProperty ? "--io.rsocket.routing.client.address.uppercase.service_name=samplefn" : "",
				"--io.rsocket.routing.client.brokers[0].tcp.host=localhost",
				"--io.rsocket.routing.client.brokers[0].tcp.port=" + routingBrokerProxyPort,
				"--io.rsocket.routing.broker.enabled=false");
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
	}
}
