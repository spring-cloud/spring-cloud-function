/*
 * Copyright 2021-present the original author or authors.
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

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @author Dave Syer
 * @author Chris Bono
 *
 * @since 3.2
 *
 */
class GrpcServer implements SmartLifecycle, EnvironmentAware {

	private Log logger = LogFactory.getLog(GrpcServer.class);

	private final FunctionGrpcProperties grpcProperties;

	private final BindableService[] grpcMessageServices;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private Server server;

	private Environment environment;

	GrpcServer(FunctionGrpcProperties grpcProperties, BindableService[] grpcMessageServices) {
		this.grpcProperties = grpcProperties;
		this.grpcMessageServices = grpcMessageServices;
	}

	@Override
	public void start() {
		this.executor.execute(() -> {
			try {
				ServerBuilder<?> serverBuilder = ServerBuilder.forPort(this.grpcProperties.getPort());
				for (int i = 0; i < this.grpcMessageServices.length; i++) {
					BindableService bindableService = this.grpcMessageServices[i];
					serverBuilder.addService(bindableService);
				}
				if (ClassUtils.isPresent("io.grpc.protobuf.services.ProtoReflectionService", null)) {
					serverBuilder.addService(ProtoReflectionService.newInstance());
				}
				this.server = serverBuilder.build();

				logger.info("Starting gRPC server");
				this.server.start();
				logger.info("gRPC server is listening on port " + this.server.getPort());

				if (environment instanceof ConfigurableEnvironment) {
					((ConfigurableEnvironment) this.environment).getPropertySources().addFirst(
						new MapPropertySource("grpcServerProps", Collections.singletonMap("local.grpc.server.port", server.getPort())));
				}
			}
			catch (Exception e) {
				stop();
				throw new IllegalStateException(e);
			}
		});
	}

	@Override
	public void stop() {
		logger.info("Shutting down gRPC server");
		this.server.shutdownNow();
		this.executor.shutdownNow();
	}

	@Override
	public boolean isRunning() {
		return this.server != null && !this.server.isShutdown();
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}
}
