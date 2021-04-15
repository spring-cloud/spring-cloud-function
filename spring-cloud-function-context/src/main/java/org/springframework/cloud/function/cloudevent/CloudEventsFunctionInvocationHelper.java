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

import java.net.URI;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.function.core.FunctionInvocationHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Implementation of {@link FunctionInvocationHelper} to support Cloud Events.
 * This is a primary (and the only) integration bridge with {@link FunctionInvocationWrapper}.
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 *
 */
public class CloudEventsFunctionInvocationHelper implements FunctionInvocationHelper<Message<?>>, ApplicationContextAware {

	private Log logger = LogFactory.getLog(this.getClass());

	private ConfigurableApplicationContext applicationContext;

	private final CloudEventHeaderEnricher cloudEventAttributesProvider;

	private CompositeMessageConverter messageConverter;

	private final Class<?> CLOUD_EVENT_CLASS;

	CloudEventsFunctionInvocationHelper(@Nullable CloudEventHeaderEnricher cloudEventHeadersProvider) {
		this.cloudEventAttributesProvider = cloudEventHeadersProvider;
		Class<?> clazz = null;
		try {
			clazz = Thread.currentThread().getContextClassLoader().loadClass("io.cloudevents.CloudEvent");
		}
		catch (Exception e) {
			// ignore
		}
		CLOUD_EVENT_CLASS = clazz;
	}

	@Override
	public boolean isRetainOuputAsMessage(Message<?> message) {
		return message.getHeaders().containsKey(MessageUtils.TARGET_PROTOCOL) || (message.getHeaders().containsKey(MessageUtils.MESSAGE_TYPE)
			&& message.getHeaders().get(MessageUtils.MESSAGE_TYPE).equals(CloudEventMessageUtils.CLOUDEVENT_VALUE));
	}

	@Override
	public Message<?> preProcessInput(Message<?> input, Object inputConverter) {
		// TODO find a way to invoke it conditionally. May be check for certain headers with all known prefixes as well as content type
		try {
			return CloudEventMessageUtils.toCanonical(input, (MessageConverter) inputConverter);
		}
		catch (Exception e) {
			return input;
		}
	}

	public void setMessageConverter(CompositeMessageConverter messageConverter) {
		this.messageConverter = messageConverter;
	}

	@Override
	public Message<?> postProcessResult(Object result, Message<?> input) {
		Object convertedResult = result;
		if (this.messageConverter != null && CLOUD_EVENT_CLASS != null && CLOUD_EVENT_CLASS.isAssignableFrom(result.getClass())) {
			convertedResult = this.messageConverter.toMessage(result, input.getHeaders());
		}
		String targetPrefix = CloudEventMessageUtils.determinePrefixToUse(input.getHeaders(), true);
		Assert.hasText(targetPrefix, "Unable to determine prefix for Cloud Event atttributes, "
				+ "which they must have according to protocol specification. Consider adding 'target-protocol' "
				+ "header with values of one of the supported protocols - [kafka, amqp, http]");
		if (logger.isDebugEnabled()) {
			logger.debug("Cloud event attributes will be prefixed with '" + targetPrefix + "'");
		}
		return this.doPostProcessResult(convertedResult, targetPrefix);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	private Message<?> doPostProcessResult(Object result, String targetPrefix) {
		Message<?> resultMessage = null; //result instanceof Message ? (Message<?>) result : null;
		CloudEventMessageBuilder<?> messageBuilder;
		if (result instanceof Message) {
			if (CloudEventMessageUtils.isCloudEvent((Message<?>) result)) {
				messageBuilder = CloudEventMessageBuilder.fromMessage((Message<?>) result);
			}
			else {
				return (Message<?>) result;
			}
		}
		else {
			messageBuilder = CloudEventMessageBuilder
					.withData(result)
					.setId(UUID.randomUUID().toString())
					.setSource(URI.create("http://spring.io/" + getApplicationName()))
					.setType(result.getClass().getName());
		}

		if (this.cloudEventAttributesProvider != null) {
			messageBuilder = this.cloudEventAttributesProvider.enrich(messageBuilder);
		}

		resultMessage = messageBuilder.build(targetPrefix);
		if (logger.isDebugEnabled()) {
			logger.debug("Cloud Event result message: " + resultMessage);
		}
		return resultMessage;
	}

	private String getApplicationName() {
		ConfigurableEnvironment environment = this.applicationContext.getEnvironment();
		String name = environment.getProperty("spring.application.name");
		return (StringUtils.hasText(name) ? name : "");
	}
}
