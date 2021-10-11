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

import org.springframework.messaging.Message;

import com.google.protobuf.GeneratedMessageV3;

/**
 *
 * @author Oleg Zhurakousky
 *
 * @param <T> instance of {@link GeneratedMessageV3}
 */
public abstract class AbstractGrpcMessageConverter<T extends GeneratedMessageV3> implements GrpcMessageConverter<T> {

	@Override
	public Message<byte[]> toSpringMessage(T grpcMessage) {
		if (this.supports(grpcMessage)) {
			return this.doToSpringMessage(grpcMessage);
		}
		return null;
	}

	@Override
	public T fromSpringMessage(Message<byte[]> springMessage, Class<T> grpcClass) {
		if (this.supports(grpcClass)) {
			return this.doFromSpringMessage(springMessage);
		}
		return null;
	}

	protected abstract Message<byte[]> doToSpringMessage(T grpcMessage);


	protected abstract T doFromSpringMessage(Message<byte[]> springMessage);

	protected boolean supports(T grpcMessage) {
//		String fieldName = grpcMessage.getAllFields().keySet().iterator().next().getFullName();
//		fieldName = fieldName.substring(0, fieldName.lastIndexOf("."));
//		System.out.println(grpcMessage.getClass().getName());
//		return fieldName.contains(grpcMessage.getClass().getSimpleName());
		return this.supports(grpcMessage.getClass());
	}

	protected abstract boolean supports(Class<? extends GeneratedMessageV3> grpcClass);
}
