/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.grpc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.grpc.MessagingServiceGrpc.MessagingServiceImplBase;
import org.springframework.context.SmartLifecycle;

class GrpcServer implements SmartLifecycle {

	private Log logger = LogFactory.getLog(GrpcServer.class);

	private final FunctionGrpcProperties grpcProperties;

	private final MessagingServiceImplBase grpcMessageService;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private Server server;

	GrpcServer(FunctionGrpcProperties grpcProperties, MessagingServiceImplBase grpcMessageService) {
		this.grpcProperties = grpcProperties;
		this.grpcMessageService = grpcMessageService;
	}

	@Override
	public void start() {
		this.executor.execute(() -> {
			try {
				this.server = ServerBuilder.forPort(this.grpcProperties.getPort())
						.addService(this.grpcMessageService)
						.build();

				logger.info("Starting gRPC server");
				this.server.start();
				logger.info("gRPC server is listening on port " + this.grpcProperties.getPort());
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
	}

	@Override
	public void stop() {
		logger.info("Shutting down gRPC server");
		this.server.shutdown();
		this.executor.shutdown();
	}

	@Override
	public boolean isRunning() {
		return this.server != null && !this.server.isShutdown();
	}
}
