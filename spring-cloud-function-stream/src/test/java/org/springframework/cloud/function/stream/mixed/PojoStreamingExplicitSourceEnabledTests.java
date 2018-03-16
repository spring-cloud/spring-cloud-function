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

package org.springframework.cloud.function.stream.mixed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = PojoStreamingExplicitSourceEnabledTests.StreamingFunctionApplication.class, properties = {
		"spring.cloud.function.stream.sink.enabled=false",
		"spring.cloud.function.stream.source.name=words,uppercase" })
public class PojoStreamingExplicitSourceEnabledTests {

	@Autowired
	Source source;

	@Autowired
	MessageCollector messageCollector;

	@Test
	public void routing() throws Exception {
		Message<?> message = messageCollector.forChannel(source.output()).poll(1000,
				TimeUnit.MILLISECONDS);
		assertThat(((Bar) message.getPayload()).getName()).isEqualTo("FOO");
	}

	@SpringBootApplication
	public static class StreamingFunctionApplication {

		@Bean
		public Function<Foo, Bar> uppercase() {
			return f -> new Bar(f.getName().toUpperCase());
		}

		@Bean
		public List<Bar> collector() {
			return new ArrayList<>();
		}

		@Bean
		public Supplier<Flux<Foo>> words(final List<Bar> list) {
			return () -> Flux.just(new Foo("foo"), new Foo("bar"));
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
