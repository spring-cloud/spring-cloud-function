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

import org.springframework.cloud.function.grpc.MessagingServiceGrpc.MessagingServiceImplBase;

import com.google.protobuf.GeneratedMessageV3;

import io.grpc.stub.StreamObserver;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 *
 */
@SuppressWarnings("rawtypes")
public class GrpcServerMessageHandler extends MessagingServiceImplBase {

	private final MessageHandlingHelper helper;

	public GrpcServerMessageHandler(MessageHandlingHelper<GeneratedMessageV3> helper) {
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


