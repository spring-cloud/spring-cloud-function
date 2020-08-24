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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.routing.broker.spring.MimeTypes;
import io.rsocket.routing.common.Id;
import io.rsocket.routing.common.Tags;
import io.rsocket.routing.common.WellKnownKey;
import io.rsocket.routing.frames.AddressFlyweight;
import io.rsocket.routing.frames.RouteSetupFlyweight;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
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
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.SocketUtils;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.rsocket.routing.broker.spring.MimeTypes.COMPOSITE_MIME_TYPE;


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
		try {
			int routingBrokerProxyPort = SocketUtils.findAvailableTcpPort();
			int routingBrokerClusterPort = SocketUtils.findAvailableTcpPort();

			// start broker
			brokerContext = new SpringApplicationBuilder(SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
				"--logging.level.io.rsocket.routing.broker=TRACE",
				"--spring.cloud.function.rsocket.enabled=false",
				"--io.rsocket.routing.client.enabled=false",
				"--io.rsocket.routing.broker.enabled=true",
				"--io.rsocket.routing.broker.tcp.port=" + routingBrokerProxyPort,
				"--io.rsocket.routing.broker.cluster.port=" + routingBrokerClusterPort);

			// start function connecting to broker, service-name=toupper
			functionContext = new SpringApplicationBuilder(SampleFunctionConfiguration.class)
				.web(WebApplicationType.NONE).run(
					"--logging.level.org.springframework.cloud.function=DEBUG",
					"--io.rsocket.routing.client.enabled=true",
					"--io.rsocket.routing.client.service-name=toupper",
					"--io.rsocket.routing.client.brokers[0].host=localhost",
					"--io.rsocket.routing.client.brokers[0].port=" + routingBrokerProxyPort,
					"--io.rsocket.routing.broker.enabled=false",
					"--spring.cloud.function.definition=uppercase");

			// setup metadata to identify the this test connecting to broker.
			RSocketStrategies strategies = functionContext.getBean(RSocketStrategies.class);
			Id testerId = Id.random();
			ByteBuf routeSetup = encodeRouteSetup(strategies, testerId, "tester");
			Payload setupPayload = DefaultPayload.create(EMPTY_BUFFER, routeSetup);

			// connect to broker
			RSocket socket = RSocketConnector.create().payloadDecoder(PayloadDecoder.ZERO_COPY)
				.metadataMimeType(COMPOSITE_MIME_TYPE.toString())
				.setupPayload(setupPayload)
				.connect(TcpClientTransport.create(routingBrokerProxyPort))
				.block();

			// setup data for request to toupper service
			ByteBuffer data = StandardCharsets.UTF_8.encode(CharBuffer.wrap("\"hello\""));
			// setup metadata for request to toupper service
			ByteBuf routingMetadata = encodeAddress(strategies, testerId, "toupper");
			Payload payload = DefaultPayload.create(data, routingMetadata.nioBuffer());
			// call toupper service
			Mono<String> result = socket.requestResponse(payload).map(Payload::getDataUtf8);

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
		}
	}

	static ByteBuf encodeAddress(RSocketStrategies strategies, Id originRouteId, String serviceName) {
		Tags tags = Tags.builder().with(WellKnownKey.SERVICE_NAME, serviceName)
			.buildTags();
		ByteBuf address = AddressFlyweight
			.encode(ByteBufAllocator.DEFAULT, originRouteId, Tags.empty(), tags);

		CompositeByteBuf composite = encodeComposite(address, MimeTypes.ROUTING_FRAME_MIME_TYPE
			.toString());
		return composite;
	}

	private static ByteBuf encodeRouteSetup(RSocketStrategies strategies, Id routeId, String serviceName) {
		Tags tags = Tags.builder()
			.with("current-time", String.valueOf(System.currentTimeMillis()))
			.with(WellKnownKey.TIME_ZONE, System.currentTimeMillis() + "")
			.buildTags();
		ByteBuf routeSetup = RouteSetupFlyweight
			.encode(ByteBufAllocator.DEFAULT, routeId, serviceName, tags);

		CompositeByteBuf composite = encodeComposite(routeSetup, MimeTypes.ROUTING_FRAME_MIME_TYPE
			.toString());
		return composite;
	}

	private static CompositeByteBuf encodeComposite(ByteBuf byteBuf, String mimeType) {
		CompositeByteBuf composite = ByteBufAllocator.DEFAULT.compositeBuffer();
		CompositeMetadataCodec
			.encodeAndAddMetadata(composite, ByteBufAllocator.DEFAULT,
				mimeType, byteBuf);
		return composite;
	}

	@EnableAutoConfiguration
	@Configuration
	public static class RoutingBrokerConfiguration {

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
