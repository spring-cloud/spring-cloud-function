/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.stream.supplier;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.stream.config.StreamConfigurationProperties;
import org.springframework.cloud.function.stream.config.SupplierInvokingMessageProducer;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marius Bogoevici
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = RestartStreamSupplierTests.StreamingFunctionApplication.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class RestartStreamSupplierTests {

	@Autowired
	Source source;

	@Autowired
	MessageCollector messageCollector;

	@Autowired
	SupplierInvokingMessageProducer<?> producer;

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void exhausted() throws Exception {
		test();
		expected.expect(NullPointerException.class);
		test();
	}

	@Test
	public void restart() throws Exception {
		test();
		assertThat(messageCollector.forChannel(source.output())).isEmpty();
		producer.stop();
		producer.start();
		test();
	}

	private void test() throws Exception {
		Message<?> result = messageCollector.forChannel(source.output()).poll(1000,
				TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isEqualTo("foo");
		assertThat(result.getHeaders().get(StreamConfigurationProperties.ROUTE_KEY))
				.isEqualTo("simpleSupplier");
	}

	@SpringBootApplication
	public static class StreamingFunctionApplication {

		@Bean
		public Supplier<String> simpleSupplier() {
			return () -> "foo";
		}
	}
}
