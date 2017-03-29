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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
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
	@Autowired
	private TestRestTemplate rest;
	@Autowired
	private TestConfiguration test;

	@Before
	public void init() {
		test.list.clear();
	}

	@Test
	public void staticResource() throws Exception {
		assertThat(rest.getForObject("/test.html", String.class)).contains("<body>Test");
	}

	@Test
	public void wordsSSE() throws Exception {
		assertThat(rest.exchange(
				RequestEntity.get(new URI("/words")).accept(EVENT_STREAM).build(),
				String.class).getBody()).isEqualTo(sse("foo", "bar"));
	}

	@Test
	public void wordsJson() throws Exception {
		assertThat(rest
				.exchange(RequestEntity.get(new URI("/words"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	@Ignore("Fix error handling")
	public void errorJson() throws Exception {
		assertThat(rest
				.exchange(RequestEntity.get(new URI("/bang"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("[\"foo\"]");
	}

	@Test
	public void words() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(RequestEntity.get(new URI("/words")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("foobar");
	}

	@Test
	public void getMore() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(RequestEntity.get(new URI("/get/more")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("foobar");
	}

	@Test
	public void bareWords() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(RequestEntity.get(new URI("/bareWords")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("foobar");
	}

	@Test
	public void updates() throws Exception {
		ResponseEntity<String> result = rest.exchange(
				RequestEntity.post(new URI("/updates")).body("one\ntwo"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo("onetwo");
	}

	@Test
	public void bareUpdates() throws Exception {
		ResponseEntity<String> result = rest.exchange(
				RequestEntity.post(new URI("/bareUpdates")).body("one\ntwo"),
				String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo("onetwo");
	}

	@Test
	public void timeoutJson() throws Exception {
		assertThat(rest
				.exchange(RequestEntity.get(new URI("/timeout"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("[\"foo\"]");
	}

	@Test
	public void emptyJson() throws Exception {
		assertThat(rest
				.exchange(RequestEntity.get(new URI("/empty"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("[]");
	}

	@Test
	public void sentences() throws Exception {
		assertThat(rest
				.exchange(RequestEntity.get(new URI("/sentences")).build(), String.class)
				.getBody()).isEqualTo("[\"go\",\"home\"][\"come\",\"back\"]");
	}

	@Test
	public void sentencesAcceptAny() throws Exception {
		assertThat(rest.exchange(
				RequestEntity.get(new URI("/sentences")).accept(MediaType.ALL).build(),
				String.class).getBody())
						.isEqualTo("[\"go\",\"home\"][\"come\",\"back\"]");
	}

	@Test
	public void sentencesAcceptJson() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.get(new URI("/sentences"))
								.accept(MediaType.APPLICATION_JSON).build(),
						String.class);
		assertThat(result.getBody()).isEqualTo("[[\"go\",\"home\"],[\"come\",\"back\"]]");
		assertThat(result.getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_JSON);
	}

	@Test
	public void sentencesAcceptSse() throws Exception {
		ResponseEntity<String> result = rest.exchange(
				RequestEntity.get(new URI("/sentences")).accept(EVENT_STREAM).build(),
				String.class);
		assertThat(result.getBody())
				.isEqualTo(sse("[\"go\",\"home\"]", "[\"come\",\"back\"]"));
		assertThat(result.getHeaders().getContentType().isCompatibleWith(EVENT_STREAM))
				.isTrue();
	}

	@Test
	public void uppercase() {
		assertThat(rest.postForObject("/uppercase", "foo\nbar", String.class))
				.isEqualTo("[FOO][BAR]");
	}

	@Test
	public void bareUppercase() {
		assertThat(rest.postForObject("/bareUppercase", "foo\nbar", String.class))
				.isEqualTo("[FOO][BAR]");
	}

	@Test
	public void transform() {
		assertThat(rest.postForObject("/transform", "foo\nbar", String.class))
				.isEqualTo("[FOO][BAR]");
	}

	@Test
	public void postMore() {
		assertThat(rest.postForObject("/post/more", "foo\nbar", String.class))
				.isEqualTo("[FOO][BAR]");
	}

	@Test
	public void postMoreFoo() {
		assertThat(rest.getForObject("/post/more/foo", String.class)).isEqualTo("[FOO]");
	}

	@Test
	public void uppercaseGet() {
		assertThat(rest.getForObject("/uppercase/foo", String.class)).isEqualTo("[FOO]");
	}

	@Test
	public void convertGet() {
		assertThat(rest.getForObject("/wrap/123", String.class)).isEqualTo("..123..");
	}

	@Test
	public void convertGetJson() throws Exception {
		assertThat(rest
				.exchange(RequestEntity.get(new URI("/entity/321"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("{\"value\":321}");
	}

	@Test
	public void uppercaseJsonArray() throws Exception {
		assertThat(rest.exchange(
				RequestEntity.post(new URI("/maps"))
						.contentType(MediaType.APPLICATION_JSON)
						// The new line in the middle is optional
						.body("[{\"value\":\"foo\"},\n{\"value\":\"bar\"}]"),
				String.class).getBody())
						.isEqualTo("{\"value\":\"FOO\"}{\"value\":\"BAR\"}");
	}

	@Test
	public void uppercaseJsonStream() throws Exception {
		assertThat(
				rest.exchange(
						RequestEntity.post(new URI("/maps"))
								.contentType(MediaType.APPLICATION_JSON)
								// TODO: make this work without newline separator
								.body("{\"value\":\"foo\"}\n{\"value\":\"bar\"}"),
						String.class).getBody())
								.isEqualTo("{\"value\":\"FOO\"}{\"value\":\"BAR\"}");
	}

	@Test
	public void uppercaseSSE() throws Exception {
		assertThat(
				rest.exchange(
						RequestEntity.post(new URI("/uppercase")).accept(EVENT_STREAM)
								.contentType(EVENT_STREAM).body(sse("foo", "bar")),
						String.class).getBody()).isEqualTo(sse("[FOO]", "[BAR]"));
	}

	private String sse(String... values) {
		return "data:" + StringUtils.arrayToDelimitedString(values, "\n\ndata:") + "\n\n";
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	public static class TestConfiguration {

		private List<String> list = new ArrayList<>();

		@Bean({ "uppercase", "transform", "post/more" })
		public Function<Flux<String>, Flux<String>> uppercase() {
			return flux -> flux.log()
					.map(value -> "[" + value.trim().toUpperCase() + "]");
		}

		@Bean
		public Function<String, String> bareUppercase() {
			return value -> "[" + value.trim().toUpperCase() + "]";
		}

		@Bean
		public Function<Flux<Integer>, Flux<String>> wrap() {
			return flux -> flux.log().map(value -> ".." + value + "..");
		}

		@Bean
		public Function<Flux<Integer>, Flux<Map<String, Object>>> entity() {
			return flux -> flux.log()
					.map(value -> Collections.singletonMap("value", value));
		}

		@Bean
		public Function<Flux<HashMap<String, String>>, Flux<Map<String, String>>> maps() {
			return flux -> flux.map(value -> {
				value.put("value", value.get("value").trim().toUpperCase());
				return value;
			});
		}

		@Bean({ "words", "get/more" })
		public Supplier<Flux<String>> words() {
			return () -> Flux.fromArray(new String[] { "foo", "bar" });
		}

		@Bean
		public Supplier<List<String>> bareWords() {
			return () -> Arrays.asList("foo", "bar");
		}

		@Bean
		public Consumer<Flux<String>> updates() {
			return flux -> flux.subscribe(value -> list.add(value));
		}

		@Bean
		public Consumer<String> bareUpdates() {
			return value -> list.add(value);
		}

		@Bean
		public Supplier<Flux<String>> bang() {
			return () -> Flux.fromArray(new String[] { "foo", "bar" }).map(value -> {
				if (value.equals("bar")) {
					throw new RuntimeException("Bar");
				}
				return value;
			});
		}

		@Bean
		public Supplier<Flux<String>> empty() {
			return () -> Flux.fromIterable(Collections.emptyList());
		}

		@Bean
		public Supplier<Flux<String>> timeout() {
			return () -> Flux.create(emitter -> {
				emitter.next("foo");
			});
		}

		@Bean
		public Supplier<Flux<List<String>>> sentences() {
			return () -> Flux.just(Arrays.asList("go", "home"),
					Arrays.asList("come", "back"));
		}

	}

}
