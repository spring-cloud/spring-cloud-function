/*
 * Copyright 2019-2019 the original author or authors.
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

import org.springframework.beans.BeansException;
import org.springframework.cloud.function.context.message.OutputMessageHeaderEnricher;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
public class CloudEventOutputMessageHeaderEnricher
		implements OutputMessageHeaderEnricher, ApplicationContextAware, Ordered {

	private ApplicationContext applicationContext;

	private CloudEventAttributesProvider cloudEventAttributesProvider;

	private static final String CLOUD_EVENT_TYPE_NAME = "io.cloudevents.api.CloudEvent";

	private static Class<?> CLOUD_EVENT_TYPE = ClassUtils.isPresent(CLOUD_EVENT_TYPE_NAME, null)
			? ClassUtils.resolveClassName(CLOUD_EVENT_TYPE_NAME, null) : null;

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public Message<?> enrich(Message<?> output) {
		Object invocationResult = output.getPayload();
		if (CLOUD_EVENT_TYPE != null && CLOUD_EVENT_TYPE.isAssignableFrom(invocationResult.getClass())) {
			// User is sending us an actual CloudEvent, so no need to guess the attributes
			return output;
		}
		CloudEventAttributes generatedCeHeaders = CloudEventMessageUtils.generateAttributes(output,
				invocationResult.getClass().getName(), getApplicationName());
		CloudEventAttributes attributes = new CloudEventAttributes(generatedCeHeaders,
				CloudEventMessageUtils.determinePrefixToUse(output.getHeaders()));
		if (cloudEventAttributesProvider != null) {
			// Global defaults can easily be changed by injecting one of these
			cloudEventAttributesProvider.generateDefaultCloudEventHeaders(attributes);
		}
		return MessageBuilder.withPayload(invocationResult).copyHeaders(attributes).build();
	}

	private String getApplicationName() {
		Environment environment = this.applicationContext.getEnvironment();
		String name = environment.getProperty("spring.application.name");
		return "http://spring.io/"
				+ (StringUtils.hasText(name) ? name : "application-" + this.applicationContext.getId());
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		if (applicationContext.getBeanNamesForType(CloudEventAttributesProvider.class).length > 0) {
			this.cloudEventAttributesProvider = applicationContext.getBean(CloudEventAttributesProvider.class);
		}
	}

}
