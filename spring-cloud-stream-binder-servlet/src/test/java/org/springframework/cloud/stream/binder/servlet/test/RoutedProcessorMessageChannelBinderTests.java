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
import org.springframework.cloud.stream.binder.servlet.MessageController;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
public class RoutedProcessorMessageChannelBinderTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void function() throws Exception {
		mockMvc.perform(post("/stream/input/words")
				.contentType(MediaType.APPLICATION_JSON).content("\"hello\""))
				.andExpect(status().isOk())
				.andExpect(header().string(MessageController.ROUTE_KEY, "words"))
				.andExpect(content().string(containsString("HELLO")));
	}

	@Test
	public void keyed() throws Exception {
		mockMvc.perform(get("/stream/words/hello")).andExpect(status().isOk())
				.andExpect(header().string(MessageController.ROUTE_KEY, "words"))
				.andExpect(content().string(containsString("HELLO")));
	}

	@Test
	public void channelAndKeyed() throws Exception {
		mockMvc.perform(get("/stream/input/words/hello")).andExpect(status().isOk())
				.andExpect(header().string(MessageController.ROUTE_KEY, "words"))
				.andExpect(content().string(containsString("HELLO")));
	}

	@Test
	public void implicit() throws Exception {
		mockMvc.perform(post("/stream/words").contentType(MediaType.APPLICATION_JSON)
				.content("\"hello\"")).andExpect(status().isOk())
				.andExpect(header().string(MessageController.ROUTE_KEY, "words"))
				.andExpect(content().string(containsString("HELLO")));
	}

	@SpringBootApplication
	@EnableBinding(Processor.class)
	protected static class TestConfiguration {
		@StreamListener(Processor.INPUT)
		@SendTo(Processor.OUTPUT)
		public Message<String> uppercase(Message<String> input) {
			return MessageBuilder.withPayload(input.getPayload().toUpperCase())
					.copyHeadersIfAbsent(input.getHeaders()).build();
		}
	}

}
