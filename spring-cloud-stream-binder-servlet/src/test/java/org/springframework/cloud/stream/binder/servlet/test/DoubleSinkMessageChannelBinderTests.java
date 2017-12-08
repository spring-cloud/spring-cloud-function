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
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
public class DoubleSinkMessageChannelBinderTests implements MessageHandler {

	@Autowired
	private Sink sink;

	@Autowired
	private Custom custom;

	@Autowired
	private MockMvc mockMvc;

	private Message<?> message;

	@Test
	public void string() throws Exception {
		sink.input().subscribe(this);
		mockMvc.perform(
				post("/stream/input").contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isAccepted())
				.andExpect(content().string(containsString("hello")));
		assertThat(this.message).isNotNull();
		sink.input().unsubscribe(this);
	}

	@Test
	public void custom() throws Exception {
		custom.input().subscribe(this);
		mockMvc.perform(
				post("/stream/custom").contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isAccepted())
				.andExpect(content().string(containsString("hello")));
		assertThat(this.message).isNotNull();
		custom.input().unsubscribe(this);
	}

	@SpringBootApplication
	@EnableBinding({ Sink.class, Custom.class })
	protected static class TestConfiguration {
	}

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		this.message = message;
	}

	interface Custom {
		@Input("custom")
		SubscribableChannel input();
	}

}
