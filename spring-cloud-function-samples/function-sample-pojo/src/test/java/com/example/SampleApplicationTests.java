/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class SampleApplicationTests {

	private HttpHeaders headers;

	@LocalServerPort
	private int port;

	private TestRestTemplate rest = new TestRestTemplate();

	@BeforeEach
	public void before() {
		this.headers = new HttpHeaders();
		this.headers.setContentType(MediaType.APPLICATION_JSON);
	}

	@Test
	public void words() {
		assertThat(this.rest
			.getForObject("http://localhost:" + this.port + "/words", String.class))
			.isEqualTo("[{\"value\":\"foo\"},{\"value\":\"bar\"}]");
	}

	@Test
	public void uppercase() {
		assertThat(this.rest.postForObject("http://localhost:" + this.port + "/uppercase",
			new HttpEntity<>("[{\"value\":\"foo\"}]", this.headers), String.class))
			.isEqualTo("[{\"value\":\"FOO\"}]");
	}

	@Test
	public void composite() {
		assertThat(this.rest
			.getForObject("http://localhost:" + this.port + "/words,uppercase",
				String.class)).isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	public void single() {
		assertThat(this.rest.postForObject("http://localhost:" + this.port + "/uppercase",
			new HttpEntity<>("{\"value\":\"foo\"}", this.headers), String.class))
			.isEqualTo("{\"value\":\"FOO\"}");
	}

	@Test
	public void lowercase() {
		assertThat(this.rest.postForObject("http://localhost:" + this.port + "/lowercase",
			new HttpEntity<>("[{\"value\":\"Foo\"}]", this.headers), String.class))
			.isEqualTo("[{\"value\":\"foo\"}]");
	}

	@Test
	public void sum() throws Exception {

		LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();

		map.put("A", Arrays.asList("1", "2", "3"));
		map.put("B", Arrays.asList("5", "6"));

		assertThat(this.rest.exchange(
			RequestEntity.post(new URI("http://localhost:" + this.port + "/sum"))
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(map),
			String.class).getBody()).isEqualTo("{\"A\":6,\"B\":11}");
	}

	@Test
	public void multipart() throws Exception {

		LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();

		map.put("A", Arrays.asList("1", "2", "3"));
		map.put("B", Arrays.asList("5", "6"));

		assertThat(this.rest.exchange(
			RequestEntity.post(new URI("http://localhost:" + this.port + "/sum"))
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.MULTIPART_FORM_DATA).body(map),
			String.class).getBody()).isEqualTo("{\"A\":6,\"B\":11}");
	}

}
