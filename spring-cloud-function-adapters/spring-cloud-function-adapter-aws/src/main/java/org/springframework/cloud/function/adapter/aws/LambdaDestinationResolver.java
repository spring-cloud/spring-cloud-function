/*
 * Copyright 2018-2020 the original author or authors.
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

import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.web.source.DestinationResolver;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

/**
 * Implementation of {@link DestinationResolver}for AWS Lambda which resolves destination
 * from `lambda-runtime-aws-request-id` message header.
 *
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 */
public class LambdaDestinationResolver implements DestinationResolver {

	private static Log logger = LogFactory.getLog(LambdaDestinationResolver.class);

	@Override
	public String destination(Supplier<?> supplier, String name, Object value) {
		if (logger.isDebugEnabled()) {
			logger.debug("Lambda invoming value: " + value);
		}
		String destination = "unknown";
		if (value instanceof Message) {
			Message<?> message = (Message<?>) value;
			MessageHeaders headers = message.getHeaders();
			if (headers.containsKey("lambda-runtime-aws-request-id")) {
				destination = (String) headers.get("lambda-runtime-aws-request-id");
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Lambda destination resolved to: " + destination);
		}
		return destination;
	}

}
