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

package org.springframework.cloud.function.cloudevent;

import java.util.Map;
import java.util.function.Function;

import org.springframework.cloud.function.context.config.SmartCompositeMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.ContentTypeResolver;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * A Cloud Events specific pre-processor that is added to {@link SmartCompositeMessageConverter}
 * to potentially modify incoming message.
 * <br><br>
 * For Cloud Event coming in binary-mode such modification implies determining
 * content type of the 'data' attribute (see {@link #getDataContentType(MessageHeaders)}
 * of Cloud Event and creating a new {@link Message} with its `contentType` set to such
 * content type while copying the rest of the headers.
 * <br><br>
 * Similar to Cloud Event coming in binary-mode, the Cloud Event coming in structured-mode
 * such modification also implies determining content type of the 'data' attribute
 * (see {@link #getDataContentType(MessageHeaders)}...
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class CloudEventDataContentTypeMessagePreProcessor implements Function<Message<?>, Message<?>> {

	private final ContentTypeResolver contentTypeResolver = new DefaultContentTypeResolver();

	private final MimeType cloudEventContentType = CloudEventMessageUtils.APPLICATION_CLOUDEVENTS;

	private final CompositeMessageConverter messageConverter;

	public CloudEventDataContentTypeMessagePreProcessor(CompositeMessageConverter messageConverter) {
		Assert.notNull(messageConverter, "'messageConverter' must not be null");
		this.messageConverter = messageConverter;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Message<?> apply(Message<?> inputMessage) {
		if (CloudEventMessageUtils.isBinary(inputMessage.getHeaders())) {
			String dataContentType = this.getDataContentType(inputMessage.getHeaders());
			Message<?> message = MessageBuilder.fromMessage(inputMessage)
					.setHeader(MessageHeaders.CONTENT_TYPE, dataContentType)
//					.setHeader("originalContentType", inputMessage.getHeaders().get(MessageHeaders.CONTENT_TYPE)) not sure about it
					.build();
			return message;
		}
		else if (this.isStructured(inputMessage)) {
			MimeType contentType = this.contentTypeResolver.resolve(inputMessage.getHeaders());
			String dataContentType = this.getDataContentType(inputMessage.getHeaders());
			String suffix = contentType.getSubtypeSuffix();
			MimeType cloudEventDeserializationContentType = MimeTypeUtils
					.parseMimeType(contentType.getType() + "/" + suffix);
			Message<?> cloudEventMessage = MessageBuilder.fromMessage(inputMessage)
					.setHeader(MessageHeaders.CONTENT_TYPE, cloudEventDeserializationContentType)
					.setHeader(CloudEventMessageUtils.CE_DATACONTENTTYPE, dataContentType).build();
			Map<String, Object> structuredCloudEvent = (Map<String, Object>) this.messageConverter
					.fromMessage(cloudEventMessage, Map.class);
			Message<?> binaryCeMessage = this.buildCeMessageFromStructured(structuredCloudEvent);
			return binaryCeMessage;
		}
		else {
			return inputMessage;
		}
	}

	private Message<?> buildCeMessageFromStructured(Map<String, Object> structuredCloudEvent) {
		MessageBuilder<?> builder = MessageBuilder.withPayload(
				structuredCloudEvent.containsKey(CloudEventMessageUtils.CE_DATA)
					? structuredCloudEvent.get(CloudEventMessageUtils.CE_DATA)
					: structuredCloudEvent.get(CloudEventMessageUtils.DATA));
		structuredCloudEvent.remove(CloudEventMessageUtils.CE_DATA);
		structuredCloudEvent.remove(CloudEventMessageUtils.DATA);
		builder.copyHeaders(structuredCloudEvent);
		return builder.build();
	}

	private String getDataContentType(MessageHeaders headers) {
		if (headers.containsKey(CloudEventMessageUtils.DATACONTENTTYPE)) {
			return (String) headers.get(CloudEventMessageUtils.DATACONTENTTYPE);
		}
		else if (headers.containsKey(CloudEventMessageUtils.CE_DATACONTENTTYPE)) {
			return (String) headers.get(CloudEventMessageUtils.CE_DATACONTENTTYPE);
		}
		else if (headers.containsKey(MessageHeaders.CONTENT_TYPE)) {
			return headers.get(MessageHeaders.CONTENT_TYPE).toString();
		}
		return MimeTypeUtils.APPLICATION_JSON_VALUE;
	}

	private boolean isStructured(Message<?> message) {
		if (!CloudEventMessageUtils.isBinary(message.getHeaders())) {
			Map<String, Object> headers = message.getHeaders();

			if (headers.containsKey(MessageHeaders.CONTENT_TYPE)) {
				MimeType contentType = this.contentTypeResolver.resolve(message.getHeaders());
				if (contentType.getType().equals(this.cloudEventContentType.getType())
						&& contentType.getSubtype().startsWith(this.cloudEventContentType.getSubtype())) {
					return true;
				}
			}
		}
		return false;
	}
}
