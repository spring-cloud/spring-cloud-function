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

import java.util.HashMap;
import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class GrpcSpringMessageConverter extends AbstractGrpcMessageConverter<GrpcSpringMessage> {

	@Override
	protected Message<byte[]> doToSpringMessage(GrpcSpringMessage grpcMessage) {
		return MessageBuilder.withPayload(grpcMessage.getPayload().toByteArray())
				.copyHeaders(grpcMessage.getHeadersMap())
				.build();
	}

	@Override
	protected GrpcSpringMessage doFromSpringMessage(Message<byte[]> springMessage) {
		Map<String, String> stringHeaders = new HashMap<>();
		springMessage.getHeaders().forEach((k, v) -> {
			stringHeaders.put(k, v.toString());
		});
		return GrpcSpringMessage.newBuilder()
				.setPayload(ByteString.copyFrom(springMessage.getPayload()))
				.putAllHeaders(stringHeaders)
				.build();
	}

	@Override
	protected boolean supports(Class<? extends GeneratedMessageV3> grpcClass) {
		return grpcClass.isAssignableFrom(GrpcSpringMessage.class);
	}
}
