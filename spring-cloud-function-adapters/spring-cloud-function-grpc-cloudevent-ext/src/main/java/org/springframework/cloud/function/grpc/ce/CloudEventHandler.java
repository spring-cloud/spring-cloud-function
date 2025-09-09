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

package org.springframework.cloud.function.grpc.ce;

import io.cloudevents.v1.CloudEventServiceGrpc.CloudEventServiceImplBase;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.stub.StreamObserver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.grpc.MessageHandlingHelper;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 *
 */
@SuppressWarnings("rawtypes")
class CloudEventHandler extends CloudEventServiceImplBase  {

	private Log logger = LogFactory.getLog(CloudEventHandler.class);

	private final MessageHandlingHelper helper;



	CloudEventHandler(MessageHandlingHelper helper) {
		this.helper = helper;
	}


	@SuppressWarnings("unchecked")
	@Override
	public void requestReply(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
		this.helper.requestReply(request, responseObserver);
	}
}


