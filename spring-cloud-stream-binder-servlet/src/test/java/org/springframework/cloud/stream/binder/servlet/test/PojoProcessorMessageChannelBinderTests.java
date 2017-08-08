/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.cloud.stream.binder.servlet.test;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "logging.level.org.springframework.web=DEBUG")
@AutoConfigureMockMvc
@DirtiesContext
public class PojoProcessorMessageChannelBinderTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void json() throws Exception {
		mockMvc.perform(post("/stream").contentType(MediaType.APPLICATION_JSON)
				.content("{\"value\":\"hello\"}")).andExpect(status().isOk())
				.andExpect(content().string(containsString("HELLO")));
	}

	@Test
	public void text() throws Exception {
		mockMvc.perform(post("/stream").contentType(MediaType.TEXT_PLAIN)
				.content("[{\"value\":\"hello\"}]")).andExpect(status().isOk())
				.andExpect(content().string(containsString("HELLO")));
	}

	@Test
	public void single() throws Exception {
		mockMvc.perform(post("/stream").contentType(MediaType.TEXT_PLAIN)
				.content("{\"value\":\"hello\"}")).andExpect(status().isOk())
				.andExpect(content().string(containsString("HELLO")));
	}

	@SpringBootApplication
	@EnableBinding(Processor.class)
	protected static class TestConfiguration {
		@StreamListener(Processor.INPUT)
		@SendTo(Processor.OUTPUT)
		public Foo uppercase(Foo input) {
			return input.toUpperCase();
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

		public Foo toUpperCase() {
			return new Foo(value.toUpperCase());
		}

	}
}
