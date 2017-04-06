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

package org.springframework.cloud.function.stream.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marius Bogoevici
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = PojoStreamingConsumerTests.StreamingSinkTest.class, properties = {
		"spring.cloud.stream.bindings.input.destination=data-in",
		"spring.cloud.function.stream.endpoint=sinkConsumer" })
public class PojoStreamingConsumerTests {

	@Autowired
	Sink sink;

	@Autowired
	List<Foo> sinkCollector;

	@Test
	public void test() throws Exception {
		sink.input().send(MessageBuilder.withPayload(new String("{\"name\":\"foo\"}")).build());
		assertThat(sinkCollector).hasSize(1);
	}

	@SpringBootApplication
	public static class StreamingSinkTest {

		@Bean
		public List<Foo> sinkCollector() {
			return new ArrayList<>();
		}

		@Bean
		public Consumer<Foo> sinkConsumer(final List<Foo> sinkCollector) {
			return s -> sinkCollector.add(s);
		}
	}

	protected static class Foo {
		private String name;

		Foo() {
		}

		public Foo(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
