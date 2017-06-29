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

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.converter.MessageConverterUtils;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Marius Bogoevici
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FluxPojoStreamingFunctionConversionTests.StreamingFunctionApplication.class)
public class FluxPojoStreamingFunctionConversionTests {

	@Autowired
	Processor processor;

	@Autowired
	MessageCollector messageCollector;

	@Test
	public void foo() throws Exception {
		processor.input().send(MessageBuilder
				.withPayload(SerializationUtils.serialize(new Foo("foo")))
				.setHeader("contentType", MessageConverterUtils.X_JAVA_SERIALIZED_OBJECT)
				.build());
		Message<?> result = messageCollector.forChannel(processor.output()).poll(1000,
				TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isInstanceOf(Foo.class);
	}

	@Test
	public void bar() throws Exception {
		processor.input().send(MessageBuilder
				.withPayload(SerializationUtils.serialize(new Bar("foo")))
				.setHeader("contentType", MessageConverterUtils.X_JAVA_SERIALIZED_OBJECT)
				.build());
		Message<?> result = messageCollector.forChannel(processor.output()).poll(1000,
				TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isInstanceOf(Bar.class);
	}

	@Test
	public void skip() throws Exception {
		processor.input().send(MessageBuilder
				.withPayload(SerializationUtils.serialize(new Spam("foo")))
				.setHeader("contentType", MessageConverterUtils.X_JAVA_SERIALIZED_OBJECT)
				.build());
		Message<?> result = messageCollector.forChannel(processor.output()).poll(100,
				TimeUnit.MILLISECONDS);
		assertThat(result).isNull();
	}

	@SpringBootApplication
	public static class StreamingFunctionApplication {

		@Bean
		public Function<Flux<Foo>, Flux<Foo>> uppercase() {
			return foos -> foos.map(f -> new Foo(f.getName().toUpperCase()));
		}

		@Bean
		public Function<Flux<Bar>, Flux<Bar>> lowercase() {
			return foos -> foos.map(f -> new Bar(f.getName().toUpperCase()));
		}

	}

	@SuppressWarnings("serial")
	protected static class Foo implements Serializable {
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

	@SuppressWarnings("serial")
	protected static class Bar implements Serializable {
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

	@SuppressWarnings("serial")
	protected static class Spam implements Serializable {
		private String name;

		Spam() {
		}

		public Spam(String name) {
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
