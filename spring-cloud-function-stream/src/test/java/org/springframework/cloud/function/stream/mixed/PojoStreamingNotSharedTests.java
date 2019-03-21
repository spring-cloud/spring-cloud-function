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

package org.springframework.cloud.function.stream.mixed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.stream.config.StreamConfigurationProperties;
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
@SpringBootTest(classes = PojoStreamingNotSharedTests.StreamingFunctionApplication.class)
public class PojoStreamingNotSharedTests {

	@Autowired
	Processor processor;

	@Autowired
	MessageCollector messageCollector;

	@Autowired
	List<Bar> collector;

	@Before
	public void init() {
		collector.clear();
	}

	@Test
	public void balance() throws Exception {
		processor.input()
				.send(MessageBuilder.withPayload("{\"name\":\"hello\"}").build());
		processor.input()
				.send(MessageBuilder.withPayload("{\"name\":\"world\"}").build());
		assertThat(messageCollector.forChannel(processor.output())).isEmpty();
		assertThat(collector).hasSize(0);
		// There should be an error in the logs (sharing disabled by default)
	}

	@Test
	public void routing() throws Exception {
		processor.input().send(MessageBuilder.withPayload("{\"name\":\"hello\"}")
				.setHeader(StreamConfigurationProperties.ROUTE_KEY, "uppercase").build());
		processor.input().send(MessageBuilder.withPayload("{\"name\":\"world\"}")
				.setHeader(StreamConfigurationProperties.ROUTE_KEY, "uppercase").build());
		Message<?> result = messageCollector.forChannel(processor.output()).poll(1000,
				TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isInstanceOf(Foo.class);
		// routing key sends messages to the function, not the consumer
		assertThat(collector).hasSize(0);
	}

	@SpringBootApplication
	public static class StreamingFunctionApplication {

		@Bean
		public Function<Foo, Foo> uppercase() {
			return f -> new Foo(f.getName().toUpperCase());
		}

		@Bean
		public List<Bar> collector() {
			return new ArrayList<>();
		}

		@Bean
		public Consumer<Bar> sink(final List<Bar> list) {
			return s -> list.add(s);
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

	protected static class Bar {
		private String name;

		Bar() {
		}

		public Bar(String name) {
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
