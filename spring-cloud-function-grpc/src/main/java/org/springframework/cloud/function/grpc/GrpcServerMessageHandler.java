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

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
//
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
//
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.grpc.MessagingServiceGrpc.MessagingServiceImplBase;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.google.protobuf.GeneratedMessageV3;
//
//import com.google.protobuf.GeneratedMessage;


/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 *
 */
@SuppressWarnings("rawtypes")
class GrpcServerMessageHandler extends MessagingServiceImplBase {

	private Log logger = LogFactory.getLog(GrpcServerMessageHandler.class);

	private final MessageHandlingHelper helper;

	private boolean running;


	GrpcServerMessageHandler(MessageHandlingHelper<GeneratedMessageV3> helper) {
		this.helper = helper;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void requestReply(GrpcSpringMessage request, StreamObserver<GrpcSpringMessage> responseObserver) {
		this.helper.requestReply(request, responseObserver);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void serverStream(GrpcSpringMessage request, StreamObserver<GrpcSpringMessage> responseObserver) {
		this.helper.serverStream(request, responseObserver);
	}

	@Override
	@SuppressWarnings("unchecked")
	public StreamObserver<GrpcSpringMessage> clientStream(StreamObserver<GrpcSpringMessage> responseObserver) {
		return this.helper.clientStream(responseObserver, GrpcSpringMessage.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	public StreamObserver<GrpcSpringMessage> biStream(StreamObserver<GrpcSpringMessage> responseObserver) {
		return this.helper.biStream(responseObserver, GrpcSpringMessage.class);
	}
}


