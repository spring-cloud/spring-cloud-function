/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.cloud.function.mvc;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.test.LocalServerPort;
import org.springframework.boot.web.server.test.client.TestRestTemplate;
import org.springframework.cloud.function.mvc.MvcRestApplicationTests.TestConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for vanilla MVC handling (no function layer). Validates the MVC customizations
 * that are added in this project independently of the specific concerns of function.
 *
 * @author Dave Syer
 *
 */
// @checkstyle:off
@SpringBootTest(classes = TestConfiguration.class, webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.main.web-application-type=servlet")
// @checkstyle:on
public class MvcRestApplicationTests {

	private static final MediaType EVENT_STREAM = MediaType.valueOf("text/event-stream");

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private TestConfiguration test;

	@BeforeEach
	public void init() {
		this.test.list.clear();
	}

	@Test
	public void wordsSSE() throws Exception {
		assertThat(this.rest.exchange(
				RequestEntity.get(new URI("/words")).accept(EVENT_STREAM).build(),
				String.class).getBody()).isEqualTo(sse("foo", "bar"));
	}

	@Test
	public void wordsJson() throws Exception {
		assertThat(this.rest
				.exchange(RequestEntity.get(new URI("/words"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	@Disabled("Fix error handling")
	public void errorJson() throws Exception {
		assertThat(this.rest
				.exchange(RequestEntity.get(new URI("/bang"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("[\"foo\"]");
	}

	@Test
	public void words() throws Exception {
		ResponseEntity<String> result = this.rest
				.exchange(RequestEntity.get(new URI("/words")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	public void foos() throws Exception {
		ResponseEntity<String> result = this.rest
				.exchange(RequestEntity.get(new URI("/foos")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"foo\"},{\"value\":\"bar\"}]");
	}

	@Test
	public void getMore() throws Exception {
		ResponseEntity<String> result = this.rest
				.exchange(RequestEntity.get(new URI("/get/more")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	@Disabled("Should this even work? Or do we need to be explicit about the JSON?")
	public void updates() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(
				RequestEntity.post(new URI("/updates")).body("one\ntwo"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(this.test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo("onetwo");
	}

	@Test
	public void updatesJson() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/updates")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"one\",\"two\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(this.test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo("[\"one\",\"two\"]");
	}

	@Test
	public void addFoos() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/addFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(this.test.list).hasSize(2);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"foo\"},{\"value\":\"bar\"}]");
	}

	@Test
	public void timeout() throws Exception {
		assertThat(this.rest
				.exchange(RequestEntity.get(new URI("/timeout")).build(), String.class)
				.getBody()).isEqualTo("[\"foo\"]");
	}

	@Test
	public void emptyJson() throws Exception {
		assertThat(this.rest
				.exchange(RequestEntity.get(new URI("/empty"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("[]");
	}

	@Test
	public void sentences() throws Exception {
		assertThat(this.rest
				.exchange(RequestEntity.get(new URI("/sentences")).build(), String.class)
				.getBody()).isEqualTo("[[\"go\",\"home\"],[\"come\",\"back\"]]");
	}

	@Test
	public void sentencesAcceptAny() throws Exception {
		assertThat(
				this.rest
						.exchange(RequestEntity.get(new URI("/sentences"))
								.accept(MediaType.ALL).build(), String.class)
						.getBody()).isEqualTo("[[\"go\",\"home\"],[\"come\",\"back\"]]");
	}

	@Test
	public void sentencesAcceptJson() throws Exception {
		ResponseEntity<String> result = this.rest
				.exchange(
						RequestEntity.get(new URI("/sentences"))
								.accept(MediaType.APPLICATION_JSON).build(),
						String.class);
		assertThat(result.getBody()).isEqualTo("[[\"go\",\"home\"],[\"come\",\"back\"]]");
		assertThat(result.getHeaders().getContentType())
				.isGreaterThanOrEqualTo(MediaType.APPLICATION_JSON);
	}

	@Test
	public void uppercase() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/uppercase")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"[FOO]\",\"[BAR]\"]");
	}

	@Test
	public void uppercaseFoos() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/upFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	public void transform() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/transform")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"[FOO]\",\"[BAR]\"]");
	}

	@Test
	public void postMore() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/post/more")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"[FOO]\",\"[BAR]\"]");
	}

	@Test
	public void uppercaseGet() {
		assertThat(this.rest.getForObject("/uppercase/foo", String.class))
				.isEqualTo("[FOO]");
	}

	@Test
	public void convertGet() {
		assertThat(this.rest.getForObject("/wrap/123", String.class))
				.isEqualTo("..123..");
	}

	@Test
	public void convertGetJson() throws Exception {
		assertThat(this.rest
				.exchange(RequestEntity.get(new URI("/entity/321"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("{\"value\":321}");
	}

	@Test
	public void uppercaseJsonStream() throws Exception {
		assertThat(
				this.rest.exchange(
						RequestEntity.post(new URI("/maps"))
								.contentType(MediaType.APPLICATION_JSON)
								.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"),
						String.class).getBody())
								.isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	public void uppercaseSSE() throws Exception {
		assertThat(this.rest.exchange(RequestEntity.post(new URI("/uppercase"))
				.accept(EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class).getBody())
						.isEqualTo(sse("[FOO]", "[BAR]"));
	}

	private String sse(String... values) {
		return "data:" + StringUtils.arrayToDelimitedString(values, "\n\ndata:") + "\n\n";
	}

	@EnableAutoConfiguration
	@RestController
	@Configuration
	public static class TestConfiguration {

		private List<String> list = new ArrayList<>();

		@PostMapping({ "/uppercase", "/transform", "/post/more" })
		public Flux<?> uppercase(@RequestBody List<String> flux) {
			return Flux.fromIterable(flux).log()
					.map(value -> "[" + value.trim().toUpperCase(Locale.ROOT) + "]");
		}

		@PostMapping("/upFoos")
		public Flux<Foo> upFoos(@RequestBody List<Foo> list) {
			return Flux.fromIterable(list).log()
					.map(value -> new Foo(value.getValue().trim().toUpperCase(Locale.ROOT)));
		}

		@GetMapping("/uppercase/{id}")
		public Mono<?> uppercaseGet(@PathVariable String id) {
			return Mono.just(id).map(value -> "[" + value.trim().toUpperCase(Locale.ROOT) + "]");
		}

		@GetMapping("/wrap/{id}")
		public Mono<?> wrapGet(@PathVariable int id) {
			return Mono.just(id).log().map(value -> ".." + value + "..");
		}

		@GetMapping("/entity/{id}")
		public Mono<Map<String, Object>> entity(@PathVariable Integer id) {
			return Mono.just(id).log()
					.map(value -> Collections.singletonMap("value", value));
		}

		@PostMapping("/maps")
		public Flux<Map<String, String>> maps(
				@RequestBody List<Map<String, String>> flux) {
			return Flux.fromIterable(flux).map(value -> {
				value.put("value", value.get("value").trim().toUpperCase(Locale.ROOT));
				return value;
			});
		}

		@GetMapping({ "/words", "/get/more" })
		public Flux<Object> words() {
			return Flux.fromArray(new String[] { "foo", "bar" });
		}

		@GetMapping("/foos")
		public Flux<Foo> foos() {
			return Flux.just(new Foo("foo"), new Foo("bar"));
		}

		@PostMapping("/updates")
		@ResponseStatus(HttpStatus.ACCEPTED)
		public Flux<?> updates(@RequestBody List<String> list) {
			Flux<String> flux = Flux.fromIterable(list).cache();
			flux.subscribe(value -> this.list.add(value));
			return flux;
		}

		@PostMapping("/addFoos")
		@ResponseStatus(HttpStatus.ACCEPTED)
		public Flux<Foo> addFoos(@RequestBody List<Foo> list) {
			Flux<Foo> flux = Flux.fromIterable(list).cache();
			flux.subscribe(value -> this.list.add(value.getValue()));
			return flux;
		}

		@GetMapping("/bang")
		public Flux<?> bang() {
			return Flux.fromArray(new String[] { "foo", "bar" }).map(value -> {
				if (value.equals("bar")) {
					throw new RuntimeException("Bar");
				}
				return value;
			});
		}

		@GetMapping("/empty")
		public Flux<?> empty() {
			return Flux.fromIterable(Collections.emptyList());
		}

		@GetMapping("/timeout")
		public Flux<?> timeout() {
			return Flux.defer(() -> Flux.<String>create(emitter -> {
				emitter.next("foo");
			}).timeout(Duration.ofMillis(100L), Flux.empty()));
		}

		@GetMapping("/sentences")
		public Flux<List<String>> sentences() {
			return Flux.just(Arrays.asList("go", "home"), Arrays.asList("come", "back"));
		}

	}

	public static class Foo {

		private String value;

		public Foo(String value) {
			this.value = value;
		}

		Foo() {
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

}
