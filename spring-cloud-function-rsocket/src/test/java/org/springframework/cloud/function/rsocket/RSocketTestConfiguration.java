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

import java.net.InetSocketAddress;
import java.time.Duration;

import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import reactor.util.retry.Retry;
import reactor.util.retry.RetrySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
@Configuration
public class RSocketTestConfiguration {

	@Bean
	@Scope("prototype")
	RSocketRequester rSocketRequester(RSocketStrategies rSocketStrategies, Environment environment,
			@Nullable RetrySpec retrySpec) {
		String port = environment.getProperty("spring.rsocket.server.port");
		Assert.hasText(port, "'spring.rsocket.server.port' must be specified");
		String host = environment.getProperty("spring.rsocket.server.address", "localhost");
		RSocket socket = RSocketConnector
				.connectWith(
						TcpClientTransport.create(InetSocketAddress.createUnresolved(host, Integer.parseInt(port))))
				.log()
				.retryWhen(retrySpec == null ? Retry.backoff(5, Duration.ofSeconds(1)) : retrySpec).block();
		return RSocketRequester.wrap(socket, MimeTypeUtils.APPLICATION_JSON, MimeTypeUtils.APPLICATION_JSON,
				rSocketStrategies);
	}
}
