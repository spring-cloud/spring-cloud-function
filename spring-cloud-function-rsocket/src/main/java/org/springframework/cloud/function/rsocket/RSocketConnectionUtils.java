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
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import reactor.core.Disposable;
import reactor.util.retry.Retry;
import reactor.util.retry.RetrySpec;

import org.springframework.lang.Nullable;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public abstract class RSocketConnectionUtils {

	public static Disposable createServerSocket(RSocket rsocket, InetSocketAddress address) {
		Disposable server = RSocketServer.create(SocketAcceptor.with(rsocket))
		.bind(TcpServerTransport.create(address)) //TODO transport can actually be selected based on address (local or tcp)??
		.subscribe();
		return server;
	}

	public static RSocket createClientSocket(InetSocketAddress address, @Nullable RetrySpec retrySpec) {
		RSocket socket = RSocketConnector.connectWith(TcpClientTransport.create(address)).log()
				.retryWhen(retrySpec == null ? Retry.backoff(5, Duration.ofSeconds(1)) : retrySpec)
				.block();
		return socket;
	}
}
