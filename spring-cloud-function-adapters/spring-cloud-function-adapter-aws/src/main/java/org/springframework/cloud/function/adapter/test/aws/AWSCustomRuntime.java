/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.adapter.test.aws;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

/**
 * AWS Custom Runtime emulator to be used for testing.
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 */
@EnableAutoConfiguration
public class AWSCustomRuntime  {

	BlockingQueue<Object> inputQueue = new ArrayBlockingQueue<>(3);

	BlockingQueue<Message<String>> outputQueue = new ArrayBlockingQueue<>(3);

	public AWSCustomRuntime(ServletWebServerApplicationContext context) {
		int port = context.getWebServer().getPort();
		System.setProperty("AWS_LAMBDA_RUNTIME_API", "localhost:" + port);
	}

	@Bean("2018-06-01/runtime/invocation/consume/response")
	Consumer<Message<String>> consume() {
		return v -> outputQueue.offer(v);
	}

	@SuppressWarnings("unchecked")
	@Bean("2018-06-01/runtime/invocation/next")
	Supplier<Message<String>> supply() {

		return () -> {
			try {
				Object value = inputQueue.poll(Long.MAX_VALUE, TimeUnit.SECONDS);
				if (!(value instanceof Message)) {
					return MessageBuilder.withPayload((String) value)
							.setHeader("Lambda-Runtime-Aws-Request-Id", "consume")
							.setHeader("Content-Type",
									MimeTypeUtils.APPLICATION_JSON)
							.build();
				}
				else {
					return (Message<String>) value;
				}

			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		};
	}

	public Message<String> exchange(Object input) {
		inputQueue.offer(input);
		try {
			return outputQueue.poll(5000, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

}
