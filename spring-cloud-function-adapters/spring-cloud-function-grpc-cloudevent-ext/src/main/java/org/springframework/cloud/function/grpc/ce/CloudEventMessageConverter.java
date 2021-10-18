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

package org.springframework.cloud.function.grpc.ce;

import java.util.Map.Entry;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import io.cloudevents.v1.proto.CloudEvent;
import io.cloudevents.v1.proto.CloudEvent.Builder;
import io.cloudevents.v1.proto.CloudEvent.CloudEventAttributeValue;
import io.cloudevents.v1.proto.CloudEvent.CloudEventAttributeValue.AttrCase;

import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.cloud.function.grpc.AbstractGrpcMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class CloudEventMessageConverter extends AbstractGrpcMessageConverter<CloudEvent> {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected Message<byte[]> doToSpringMessage(CloudEvent cloudEvent) {
		MessageBuilder builder = MessageBuilder.withPayload(cloudEvent.getTextData());
		builder.setHeader(CloudEventMessageUtils.TYPE, cloudEvent.getType());
		builder.setHeader(CloudEventMessageUtils.SOURCE, cloudEvent.getSource());
		builder.setHeader(CloudEventMessageUtils.ID, cloudEvent.getId());
		builder.setHeader(CloudEventMessageUtils.SPECVERSION, cloudEvent.getId());

		for (Entry<String, CloudEventAttributeValue> attributeEntry : cloudEvent.getAttributesMap().entrySet()) {
			AttrCase attrCase = attributeEntry.getValue().getAttrCase();
			if (attrCase.equals(AttrCase.CE_BOOLEAN)) {
				builder.setHeader(attributeEntry.getKey(), attributeEntry.getValue().getCeBoolean());
			}
			else if (attrCase.equals(AttrCase.CE_BYTES)) {
				builder.setHeader(attributeEntry.getKey(), attributeEntry.getValue().getCeBytes());
			}
			else if (attrCase.equals(AttrCase.CE_INTEGER)) {
				builder.setHeader(attributeEntry.getKey(), attributeEntry.getValue().getCeInteger());
			}
			else if (attrCase.equals(AttrCase.CE_STRING)) {
				builder.setHeader(attributeEntry.getKey(), attributeEntry.getValue().getCeString());
			}
			else if (attrCase.equals(AttrCase.CE_TIMESTAMP)) {
				builder.setHeader(attributeEntry.getKey(), attributeEntry.getValue().getCeTimestamp());
			}
			else if (attrCase.equals(AttrCase.CE_URI)) {
				builder.setHeader(attributeEntry.getKey(), attributeEntry.getValue().getCeUri());
			}
			else if (attrCase.equals(AttrCase.CE_URI_REF)) {
				builder.setHeader(attributeEntry.getKey(), attributeEntry.getValue().getCeUriRef());
			}
			else {
				throw new IllegalStateException("Unknown type for attribute " + attributeEntry.getKey());
			}

		}
		return builder.build();
	}

	@Override
	protected CloudEvent doFromSpringMessage(Message<byte[]> springMessage) {
		Builder builder = CloudEvent.newBuilder()
				.setTextDataBytes(ByteString.copyFrom(springMessage.getPayload()))
				.setType(CloudEventMessageUtils.getType(springMessage))
				.setSource(CloudEventMessageUtils.getSource(springMessage).toString())
				.setId(CloudEventMessageUtils.getId(springMessage))
				.setSpecVersion(CloudEventMessageUtils.getSpecVersion(springMessage));


		for (Entry<String, Object> entry : springMessage.getHeaders().entrySet()) {
			builder.putAttributes(entry.getKey(), CloudEventAttributeValue.newBuilder().setCeString(entry.getValue().toString()).build());
		}
		return builder.build();

	}

	@Override
	protected boolean supports(Class<? extends GeneratedMessageV3> grpcClass) {
		return grpcClass.isAssignableFrom(CloudEvent.class);
	}
}
