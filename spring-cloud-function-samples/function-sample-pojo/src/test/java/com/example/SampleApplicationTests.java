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
package com.example;

import java.net.URI;
import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class SampleApplicationTests {

	@LocalServerPort
	private int port;
	private TestRestTemplate rest = new TestRestTemplate();

	@Test
	public void words() {
		assertThat(rest.getForObject("http://localhost:" + port + "/words", String.class))
				.isEqualTo("[{\"value\":\"foo\"},{\"value\":\"bar\"}]");
	}

	@Test
	public void uppercase() {
		assertThat(rest.postForObject("http://localhost:" + port + "/uppercase",
				"[{\"value\":\"foo\"}]", String.class))
						.isEqualTo("[{\"value\":\"FOO\"}]");
	}

	@Test
	public void composite() {
		assertThat(rest.getForObject("http://localhost:" + port + "/words,uppercase",
				String.class)).isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	public void single() {
		assertThat(rest.postForObject("http://localhost:" + port + "/uppercase",
				"{\"value\":\"foo\"}", String.class)).isEqualTo("{\"value\":\"FOO\"}");
	}

	@Test
	public void lowercase() {
		assertThat(rest.postForObject("http://localhost:" + port + "/lowercase",
				"[{\"value\":\"Foo\"}]", String.class))
						.isEqualTo("[{\"value\":\"foo\"}]");
	}

	@Test
	public void sum() throws Exception {

		LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();

		map.put("A", Arrays.asList("1", "2", "3"));
		map.put("B", Arrays.asList("5", "6"));

		assertThat(rest.exchange(
				RequestEntity.post(new URI("http://localhost:" + port + "/sum"))
						.accept(MediaType.APPLICATION_JSON)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(map),
				String.class).getBody()).isEqualTo("[{\"A\":6,\"B\":11}]");
	}

	@Test
	// @Ignore
	public void multipart() throws Exception {

		LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();

		map.put("A", Arrays.asList("1", "2", "3"));
		map.put("B", Arrays.asList("5", "6"));

		assertThat(rest.exchange(
				RequestEntity.post(new URI("http://localhost:" + port + "/sum")).accept(MediaType.APPLICATION_JSON)
						.contentType(MediaType.MULTIPART_FORM_DATA).body(map),
				String.class).getBody()).isEqualTo("[{\"A\":6,\"B\":11}]");
	}
}
