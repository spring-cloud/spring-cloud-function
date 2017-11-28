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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
@SpringBootTest(classes = PojoStreamingExplicitEndpointTests.StreamingFunctionApplication.class, properties = {
		"spring.cloud.function.stream.default-route=uppercase",
		"logging.level.org.springframework.integration=DEBUG", "debug=TRUE" })
public class PojoStreamingExplicitEndpointTests {

	@Autowired
	Processor processor;

	@Autowired
	MessageCollector messageCollector;
	
	@Autowired
	StreamingFunctionApplication app;

	@Test
	public void testDefaultEndpoint() throws Exception {
		processor.input()
				.send(MessageBuilder.withPayload("{\"name\":\"hello\"}").build());
		Message<?> result = messageCollector.forChannel(processor.output()).poll(1000,
				TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isInstanceOf(Foo.class);
	}

	@Test
	public void testRoutingBeatsDefaultEndpoint() throws Exception {
		processor.input()
				.send(MessageBuilder.withPayload("{\"name\":\"hello\"}").setHeader(StreamConfigurationProperties.ROUTE_KEY, "sink").build());
		assertThat(app.foos).hasSize(1);
	}

	@SpringBootApplication
	public static class StreamingFunctionApplication {
		
		private List<Foo> foos = new ArrayList<>();

		@Bean
		public Function<Foo, Foo> uppercase() {
			return f -> new Foo(f.getName().toUpperCase());
		}

		@Bean
		public Supplier<Foo> foos() {
			return () -> new Foo("world");
		}

		@Bean
		public Consumer<Foo> sink() {
			return foo -> foos.add(foo);
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
