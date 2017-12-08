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
import org.springframework.cloud.stream.binder.servlet.MessageController;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
public class RoutedSourceMessageChannelBinderTests {

	@Autowired
	private Source source;

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void supplier() throws Exception {
		source.output().send(MessageBuilder.withPayload("hello")
				.setHeader(MessageController.ROUTE_KEY, "words").build());
		mockMvc.perform(get("/stream/output/words")).andExpect(status().isOk())
				.andExpect(content().string(containsString("hello")));
	}

	@Test
	public void implicit() throws Exception {
		source.output().send(MessageBuilder.withPayload("hello")
				.setHeader(MessageController.ROUTE_KEY, "words").build());
		mockMvc.perform(get("/stream/words")).andExpect(status().isOk())
				.andExpect(content().string(containsString("hello")));
	}

	@SpringBootApplication
	@EnableBinding(Source.class)
	protected static class TestConfiguration {
	}

}
