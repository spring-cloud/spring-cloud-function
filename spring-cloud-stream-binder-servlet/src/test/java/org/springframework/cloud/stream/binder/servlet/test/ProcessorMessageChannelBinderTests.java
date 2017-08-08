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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
public class ProcessorMessageChannelBinderTests {

	@Autowired
	private Processor processor;

	@Autowired
	private MockMvc mockMvc;

	@Before
	public void init() throws Exception {
		mockMvc.perform(get("/stream/output?purge=true")).andReturn();
	}

	@Test
	public void supplier() throws Exception {
		processor.output().send(MessageBuilder.withPayload("hello").build());
		mockMvc.perform(get("/stream/output")).andExpect(status().isOk())
				.andExpect(content().string(containsString("hello")));
	}

	@Test
	public void missing() throws Exception {
		// Missing route is not found if channel is explicit
		mockMvc.perform(get("/stream/output/missing")).andExpect(status().isNotFound())
				.andExpect(content().string(equalTo("")));
	}

	@Test
	public void empty() throws Exception {
		// Missing route where channel can be inferred (there is an input channel) is
		// going to be passed on as a body
		mockMvc.perform(get("/stream/missing")).andExpect(status().isOk())
				.andExpect(content().string(equalTo("MISSING")));
	}

	@Test
	public void function() throws Exception {
		mockMvc.perform(post("/stream/input").contentType(MediaType.APPLICATION_JSON)
				.content("\"hello\"")).andExpect(status().isOk())
				.andExpect(content().string(containsString("HELLO")));
	}

	@Test
	public void keyed() throws Exception {
		mockMvc.perform(get("/stream/hello")).andExpect(status().isOk())
				.andExpect(content().string(containsString("HELLO")));
	}

	@Test
	public void implicit() throws Exception {
		mockMvc.perform(post("/stream").contentType(MediaType.APPLICATION_JSON)
				.content("\"hello\"")).andExpect(status().isOk())
				.andExpect(content().string(containsString("HELLO")));
	}

	@Test
	public void string() throws Exception {
		mockMvc.perform(
				post("/stream/input").contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isOk()).andExpect(content().string(equalTo("HELLO")));
	}

	@Test
	public void multi() throws Exception {
		mockMvc.perform(post("/stream/input").contentType(MediaType.APPLICATION_JSON)
				.content("[\"hello\",\"world\"]")).andExpect(status().isOk())
				.andExpect(content().string("[\"HELLO\",\"WORLD\"]"));
	}

	@SpringBootApplication
	@EnableBinding(Processor.class)
	protected static class TestConfiguration {
		@StreamListener(Processor.INPUT)
		@SendTo(Processor.OUTPUT)
		public String uppercase(String input) {
			return input.toUpperCase();
		}

		public static void main(String[] args) throws Exception {
			SpringApplication.run(
					ProcessorMessageChannelBinderTests.TestConfiguration.class,
					"--logging.level.root=INFO");
		}

	}

}
