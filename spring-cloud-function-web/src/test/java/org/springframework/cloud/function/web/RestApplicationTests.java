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
package org.springframework.cloud.function.web;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class RestApplicationTests {

	private static final MediaType EVENT_STREAM = MediaType.valueOf("text/event-stream");
	@LocalServerPort
	private int port;
	private TestRestTemplate rest = new TestRestTemplate();

	@Test
	public void wordsSSE() throws Exception {
		assertThat(rest.exchange(
				RequestEntity.get(new URI("http://localhost:" + port + "/words"))
						.accept(EVENT_STREAM).build(),
				String.class).getBody()).isEqualTo(sse("foo", "bar"));
	}

	@Test
	public void wordsJson() throws Exception {
		assertThat(rest
				.exchange(
						RequestEntity.get(new URI("http://localhost:" + port + "/words"))
								.accept(MediaType.APPLICATION_JSON).build(),
						String.class)
				.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	public void words() throws Exception {
		assertThat(rest.exchange(
				RequestEntity.get(new URI("http://localhost:" + port + "/words")).build(),
				String.class).getBody()).isEqualTo("foobar");
	}

	@Test
	public void sentences() throws Exception {
		assertThat(rest.exchange(RequestEntity
				.get(new URI("http://localhost:" + port + "/sentences")).build(),
				String.class).getBody())
						.isEqualTo("[\"go\",\"home\"][\"come\",\"back\"]");
	}

	@Test
	public void sentencesAcceptAny() throws Exception {
		assertThat(rest.exchange(
				RequestEntity.get(new URI("http://localhost:" + port + "/sentences"))
						.accept(MediaType.ALL).build(),
				String.class).getBody())
						.isEqualTo("[\"go\",\"home\"][\"come\",\"back\"]");
	}

	@Test
	public void sentencesAcceptJson() throws Exception {
		ResponseEntity<String> result = rest.exchange(
				RequestEntity.get(new URI("http://localhost:" + port + "/sentences"))
						.accept(MediaType.APPLICATION_JSON).build(),
				String.class);
		assertThat(result.getBody()).isEqualTo("[[\"go\",\"home\"],[\"come\",\"back\"]]");
		// TODO: test this
		// assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
	}

	@Test
	public void sentencesAcceptSse() throws Exception {
		assertThat(rest.exchange(
				RequestEntity.get(new URI("http://localhost:" + port + "/sentences"))
						.accept(EVENT_STREAM).build(),
				String.class).getBody())
						.isEqualTo(sse("[\"go\",\"home\"]", "[\"come\",\"back\"]"));
	}

	@Test
	public void uppercase() {
		assertThat(rest.postForObject("http://localhost:" + port + "/uppercase",
				"foo\nbar", String.class)).isEqualTo("[FOO][BAR]");
	}

	@Test
	public void uppercaseJsonArray() throws Exception {
		assertThat(rest.exchange(
				RequestEntity.post(new URI("http://localhost:" + port + "/maps"))
						.contentType(MediaType.APPLICATION_JSON)
						// The new line in the middle is optional
						.body("[{\"value\":\"foo\"},\n{\"value\":\"bar\"}]"),
				String.class).getBody())
						.isEqualTo("{\"value\":\"FOO\"}{\"value\":\"BAR\"}");
	}

	@Test
	public void uppercaseJsonStream() throws Exception {
		assertThat(rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/maps"))
								.contentType(MediaType.APPLICATION_JSON)
								// TODO: make this work without newline separator
								.body("{\"value\":\"foo\"}\n{\"value\":\"bar\"}"),
						String.class)
				.getBody()).isEqualTo("{\"value\":\"FOO\"}{\"value\":\"BAR\"}");
	}

	@Test
	public void uppercaseSSE() throws Exception {
		assertThat(rest.exchange(RequestEntity
				.post(new URI("http://localhost:" + port + "/uppercase"))
				.accept(EVENT_STREAM).contentType(EVENT_STREAM).body(sse("foo", "bar")),
				String.class).getBody()).isEqualTo(sse("[FOO]", "[BAR]"));
	}

	private String sse(String... values) {
		return "data:" + StringUtils.arrayToDelimitedString(values, "\n\ndata:") + "\n\n";
	}

	@SpringBootApplication
	public static class TestConfiguration {

		@Bean
		public Function<Flux<String>, Flux<String>> uppercase() {
			return flux -> flux.log()
					.map(value -> "[" + value.trim().toUpperCase() + "]");
		}

		@Bean
		public Function<Flux<HashMap<String, String>>, Flux<Map<String, String>>> maps() {
			return flux -> flux.map(value -> {
				value.put("value", value.get("value").trim().toUpperCase());
				return value;
			});
		}

		@Bean
		public Supplier<Flux<String>> words() {
			return () -> Flux.fromArray(new String[] { "foo", "bar" });
		}

		@Bean
		public Supplier<Flux<List<String>>> sentences() {
			return () -> Flux.just(Arrays.asList("go", "home"),
					Arrays.asList("come", "back"));
		}

	}

}
