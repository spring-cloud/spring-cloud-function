/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.stream.function;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marius Bogoevici
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FluxStreamingFunctionTests.StreamingFunctionApplication.class, properties = {
		"spring.cloud.stream.bindings.input.destination=data-in",
		"spring.cloud.stream.bindings.output.destination=data-out",
		"spring.cloud.function.stream.endpoint=uppercase" })
public class FluxStreamingFunctionTests {

	@Autowired
	Processor processor;

	@Autowired
	MessageCollector messageCollector;

	@Test
	public void test() throws Exception {
		processor.input().send(MessageBuilder.withPayload("foo").build());
		Message<?> result = messageCollector.forChannel(processor.output()).poll(1000, TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isEqualTo("FOO");
	}

	@SpringBootApplication
	public static class StreamingFunctionApplication {

		@Bean
		public Function<Flux<String>, Flux<String>> uppercase() {
			return f-> f.map(s -> s.toUpperCase());
		}
	}
}
