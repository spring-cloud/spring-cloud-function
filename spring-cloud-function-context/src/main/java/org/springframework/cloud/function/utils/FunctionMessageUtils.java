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


package org.springframework.cloud.function.utils;

import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

/**
 *
 * !!! INTERNAL ONLY !!!
 *
 * @author Oleg Zhurakousky
 *
 */
public final class FunctionMessageUtils {

	private FunctionMessageUtils() {

	}

	public static String getSourceType(String functionDefinition, Message<?> message) {
		return determineSourceFromHeaders(message.getHeaders());
	}

	public static String getTargetType(String functionDefinition, Message<?> message) {
		return message.getHeaders().containsKey(MessageUtils.TARGET_PROTOCOL) ? (String) message.getHeaders().get(MessageUtils.TARGET_PROTOCOL) : "unknown";
	}

	private static String determineSourceFromHeaders(MessageHeaders headers) {
		for (String key : headers.keySet()) {
			if (key.equals(MessageUtils.SOURCE_TYPE)) {
				return (String) headers.get(MessageUtils.SOURCE_TYPE);
			}
			else if (key.startsWith("amqp_")) {
				return "amqp";
			}
			else if (key.startsWith("kafka_")) {
				return "kafka";
			}
			else if (key.startsWith("aws_")) {
				return "aws";
			}
			else if (key.startsWith("solace_")) {
				return "solace";
			}
			else if (key.toLowerCase().equals("user-agent") || key.toLowerCase().equals("accept-encoding") || key.toLowerCase().equals("host")) {
				return "http";
			}
			// add rsocket
		}
		return "origin";
	}
}
