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

import io.grpc.stub.StreamObserver;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.grpc.MessagingServiceGrpc.MessagingServiceImplBase;
import org.springframework.messaging.Message;

class GrpcMessagingServiceImpl extends MessagingServiceImplBase {

	private final FunctionInvocationWrapper function;

	GrpcMessagingServiceImpl(FunctionProperties funcProperties, FunctionCatalog functionCatalog) {
		this.function = functionCatalog.lookup(funcProperties.getDefinition(), "application/json");
	}


	@Override
	public void requestReply(GrpcMessage request, StreamObserver<GrpcMessage> responseObserver) {
		Message<byte[]> message = GrpcUtils.fromGrpcMessage(request);
		Message<byte[]> replyMessage = (Message<byte[]>) this.function.apply(message);

		GrpcMessage reply = GrpcUtils.toGrpcMessage(replyMessage);
		/*
		 * The above is effectively echo. This is where we plug in function invocation
		 */
		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}
//
//	@Override
//	public void serverStream(GrpcMessage request,
//			StreamObserver<GrpcMessage> responseObserver) {
//
//	}
//
//	@Override
//	public StreamObserver<GrpcMessage> clientStream(
//			StreamObserver<GrpcMessage> responseObserver) {
//		return null;
//	}
//
//	@Override
//	public StreamObserver<GrpcMessage> biStream(
//			StreamObserver<GrpcMessage> responseObserver) {
//		return null;
//	}
}
