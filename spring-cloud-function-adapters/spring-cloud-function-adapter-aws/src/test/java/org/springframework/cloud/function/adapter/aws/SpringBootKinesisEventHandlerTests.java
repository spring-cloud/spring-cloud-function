/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.adapter.aws;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.amazonaws.kinesis.agg.AggRecord;
import com.amazonaws.kinesis.agg.RecordAggregator;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;

/**
 * @author Halvdan Hoem Grelland
 */
public class SpringBootKinesisEventHandlerTests {

	private static final ObjectMapper mapper = new ObjectMapper();

	private SpringBootKinesisEventHandler<Foo, Bar> handler;

	@Test
	public void functionBeanHandlesKinesisEvent() throws Exception {
		handler = new SpringBootKinesisEventHandler<>(FunctionConfig.class);
		handler.initialize();

		KinesisEvent event = asKinesisEvent(singletonList(new Foo("foo")));

		List<Bar> output = handler.handleRequest(event, null);

		assertThat(output).containsExactly(new Bar("FOO"));
	}

	@Test
	public void functionBeanHandlesAggregatedKinesisEvent() throws Exception {
		handler = new SpringBootKinesisEventHandler<>(FunctionConfig.class);
		handler.initialize();

		List<Foo> events = asList(new Foo("foo"), new Foo("bar"), new Foo("baz"));
		KinesisEvent aggregatedEvent = asAggregatedKinesisEvent(events);

		List<Bar> output = handler.handleRequest(aggregatedEvent, null);

		assertThat(output)
				.containsExactly(new Bar("FOO"), new Bar("BAR"), new Bar("BAZ"));
	}

	@Test
	public void functionMessageBean() throws Exception  {
		handler = new SpringBootKinesisEventHandler<>(FunctionMessageConfig.class);
		handler.initialize();

		KinesisEvent event = asKinesisEvent(asList(new Foo("foo"), new Foo("bar")));

		List<Bar> output = handler.handleRequest(event, null);

		assertThat(output)
				.containsExactly(new Bar("FOO"), new Bar("BAR"));
	}

	@Configuration
	@Import({
			ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class
	})
	protected static class FunctionConfig {
		@Bean
		public Function<Foo, Bar> function() {
			return foo -> new Bar(foo.getValue().toUpperCase());
		}
	}

	@Configuration
	@Import({
			ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class
	})
	protected static class FunctionMessageConfig {
		@Bean
		public Function<Message<Foo>, Bar> function() {
			return foo -> new Bar(foo.getPayload().getValue().toUpperCase());
		}
	}

	protected static class Foo {

		private String value;

		public Foo() {
		}

		public Foo(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	protected static class Bar {

		private String value;

		public Bar() {
		}

		public Bar(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Bar bar = (Bar) o;
			return Objects.equals(value, bar.value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(value);
		}
	}

	private static KinesisEvent asKinesisEvent(List<Object> payloads) {
		KinesisEvent kinesisEvent = new KinesisEvent();

		List<KinesisEvent.KinesisEventRecord> kinesisEventRecords = new ArrayList<>();

		for (Object payload : payloads) {
			KinesisEvent.Record record = new KinesisEvent.Record();
			record.setData(asJsonByteBuffer(payload));

			KinesisEvent.KinesisEventRecord kinesisEventRecord =
					new KinesisEvent.KinesisEventRecord();
			kinesisEventRecord.setKinesis(record);

			kinesisEventRecords.add(kinesisEventRecord);
		}

		kinesisEvent.setRecords(kinesisEventRecords);

		return kinesisEvent;
	}

	private static KinesisEvent asAggregatedKinesisEvent(List<?> payloads) {
		RecordAggregator aggregator = new RecordAggregator();

		payloads.stream()
				.map(SpringBootKinesisEventHandlerTests::asJsonByteBuffer)
				.forEach(buffer -> {
					try {
						aggregator.addUserRecord("fakePartitionKey", buffer.array());
					} catch (Exception e) {
						fail("Creating aggregated record failed");
					}
				});

		AggRecord aggRecord = aggregator.clearAndGet();

		KinesisEvent.Record record = new KinesisEvent.Record();
		record.setData(ByteBuffer.wrap(aggRecord.toRecordBytes()));

		KinesisEvent.KinesisEventRecord wrappingRecord = new KinesisEvent.KinesisEventRecord();
		wrappingRecord.setKinesis(record);
		wrappingRecord.setEventVersion("1.0");

		KinesisEvent event = new KinesisEvent();
		event.setRecords(singletonList(wrappingRecord));

		return event;
	}

	private static ByteBuffer asJsonByteBuffer(Object object) {
		try {
			return ByteBuffer.wrap(mapper.writeValueAsBytes(object));
		} catch (JsonProcessingException e) {
			fail("Setting up test data failed", e);
			throw new RuntimeException(e);
		}
	}
}
