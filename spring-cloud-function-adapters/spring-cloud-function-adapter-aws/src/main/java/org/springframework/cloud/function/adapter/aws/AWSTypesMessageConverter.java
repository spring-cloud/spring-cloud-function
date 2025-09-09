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

package org.springframework.cloud.function.adapter.aws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import com.amazonaws.services.lambda.runtime.serialization.events.LambdaEventSerializers;
import com.amazonaws.services.lambda.runtime.serialization.events.serializers.S3EventSerializer;

import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.cloud.function.context.config.JsonMessageConverter;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeType;

/**
 * Implementation of {@link MessageConverter} which uses Jackson or Gson libraries to do the
 * actual conversion via {@link JsonMapper} instance.
 *
 * @author Oleg Zhurakousky
 *
 * @since 3.2
 */
class AWSTypesMessageConverter extends JsonMessageConverter {

	private final JsonMapper jsonMapper;

	@SuppressWarnings("rawtypes")
	private final AtomicReference<S3EventSerializer> s3EventSerializer = new AtomicReference<>();

	AWSTypesMessageConverter(JsonMapper jsonMapper) {
		this(jsonMapper, new MimeType("application", "json"), new MimeType(CloudEventMessageUtils.APPLICATION_CLOUDEVENTS.getType(),
				CloudEventMessageUtils.APPLICATION_CLOUDEVENTS.getSubtype() + "+json"));
	}

	AWSTypesMessageConverter(JsonMapper jsonMapper, MimeType... supportedMimeTypes) {
		super(jsonMapper, supportedMimeTypes);
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected boolean canConvertFrom(Message<?> message, @Nullable Class<?> targetClass) {
		if (message.getHeaders().containsKey(AWSLambdaUtils.AWS_API_GATEWAY)) {
			return ((boolean) message.getHeaders().get(AWSLambdaUtils.AWS_API_GATEWAY));
		}
		if (message.getHeaders().containsKey(AWSLambdaUtils.AWS_EVENT)) {
			return ((boolean) message.getHeaders().get(AWSLambdaUtils.AWS_EVENT));
		}
		//TODO Do we really need the ^^ above? It seems like the line below dows the trick
		else if (targetClass.getPackage() != null &&
				targetClass.getPackage().getName().startsWith("com.amazonaws.services.lambda.runtime.events")) {
			return true;
		}
		return false;
	}

	@Override
	protected Object convertFromInternal(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
		if (message.getPayload().getClass().isAssignableFrom(targetClass)) {
			return message.getPayload();
		}
		if (targetClass.getPackage() != null &&
				targetClass.getPackage().getName().startsWith("com.amazonaws.services.lambda.runtime.events")) {
			PojoSerializer<?> serializer = LambdaEventSerializers.serializerFor(targetClass, Thread.currentThread().getContextClassLoader());
			Object event = serializer.fromJson(new ByteArrayInputStream((byte[]) message.getPayload()));
			return event;
		}
		else {
			Map<String, String> structMessage = this.jsonMapper.fromJson(message.getPayload(), Map.class);
			if (targetClass.isAssignableFrom(Map.class)) {
				return structMessage;
			}
			else {
				Object body;
				if (structMessage.containsKey("body")) {
					body = structMessage.get("body");
				}
				else {
					body = message.getPayload();
				}
				Object convertedResult = this.jsonMapper.fromJson(body, targetClass);
				return convertedResult;
			}
		}
	}

	@Override
	protected boolean canConvertTo(Object payload, @Nullable MessageHeaders headers) {
		if (!supportsMimeType(headers)) {
			return false;
		}
		return true;
	}


	@SuppressWarnings("unchecked")
	@Override
	protected Object convertToInternal(Object payload, @Nullable MessageHeaders headers,
			@Nullable Object conversionHint) {
		if (payload instanceof String && headers.containsKey(AWSLambdaUtils.IS_BASE64_ENCODED)  && (boolean) headers.get(AWSLambdaUtils.IS_BASE64_ENCODED)) {
			return ((String) payload).getBytes(StandardCharsets.UTF_8);
		}
		if (payload.getClass().getName().equals("com.amazonaws.services.lambda.runtime.events.S3Event")) {
			if (this.s3EventSerializer.get() == null) {
				this.s3EventSerializer.set(new S3EventSerializer<>().withClassLoader(ClassUtils.getDefaultClassLoader()));
			}
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			this.s3EventSerializer.get().toJson(payload, stream);
			return stream.toByteArray();
		}


		return jsonMapper.toJson(payload);
	}

}
