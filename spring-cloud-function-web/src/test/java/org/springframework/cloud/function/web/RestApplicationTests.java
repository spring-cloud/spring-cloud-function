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
import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Qualifier;
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

	private static final MediaType EVENT_STREAM = MediaType.TEXT_EVENT_STREAM;
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
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	public void foos() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(RequestEntity.get(new URI("/foos")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"foo\"},{\"value\":\"bar\"}]");
	}

	@Test
	public void qualifierFoos() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/foos")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"[FOO]\"},{\"value\":\"[BAR]\"}]");
	}

	@Test
	public void getMore() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(RequestEntity.get(new URI("/get/more")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	public void bareWords() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(RequestEntity.get(new URI("/bareWords")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	@Ignore("Should this even work? Or do we need to be explicit about the JSON?")
	public void updates() throws Exception {
		ResponseEntity<String> result = rest.exchange(
				RequestEntity.post(new URI("/updates")).body("one\ntwo"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo("onetwo");
	}

	@Test
	public void updatesJson() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/updates")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"one\",\"two\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo("[\"one\",\"two\"]");
	}

	@Test
	public void addFoos() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/addFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"foo\"},{\"value\":\"bar\"}]");
	}

	@Test
	public void bareUpdates() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/bareUpdates")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"one\",\"two\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo("[\"one\",\"two\"]");
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
				.getBody()).isEqualTo("[[\"go\",\"home\"],[\"come\",\"back\"]]");
	}

	@Test
	public void sentencesAcceptAny() throws Exception {
		assertThat(rest.exchange(
				RequestEntity.get(new URI("/sentences")).accept(MediaType.ALL).build(),
				String.class).getBody())
						.isEqualTo("[[\"go\",\"home\"],[\"come\",\"back\"]]");
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
				.isGreaterThanOrEqualTo(MediaType.APPLICATION_JSON);
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
	public void uppercase() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/uppercase")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	public void uppercaseSingleValue() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/uppercase")).contentType(MediaType.TEXT_PLAIN)
				.body("foo"), String.class);
		assertThat(result.getBody()).isEqualTo("(FOO)");
	}

	@Test
	@Ignore("WebFlux would split the request body into lines: TODO make this work the same")
	public void uppercasePlainText() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/uppercase")).contentType(MediaType.TEXT_PLAIN)
				.body("foo\nbar"), String.class);
		assertThat(result.getBody()).isEqualTo("(FOO)(BAR)");
	}

	@Test
	public void uppercaseFoos() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/upFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	public void uppercaseFoo() throws Exception {
		// Single Foo can be parsed
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/upFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("{\"value\":\"foo\"}"), String.class);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"FOO\"}]");
	}

	@Test
	public void bareUppercaseFoos() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/bareUpFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	public void bareUppercaseFoo() throws Exception {
		// Single Foo can be parsed and returns a single value if the function is defined that way
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/bareUpFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("{\"value\":\"foo\"}"), String.class);
		assertThat(result.getBody())
				.isEqualTo("{\"value\":\"FOO\"}");
	}

	@Test
	public void bareUppercase() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/bareUppercase")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	public void transform() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/transform")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	public void postMore() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/post/more")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	public void postMoreFoo() {
		assertThat(rest.getForObject("/post/more/foo", String.class)).isEqualTo("(FOO)");
	}

	@Test
	public void uppercaseGet() {
		assertThat(rest.getForObject("/uppercase/foo", String.class)).isEqualTo("(FOO)");
	}

	@Test
	public void convertGet() {
		assertThat(rest.getForObject("/wrap/123", String.class)).isEqualTo("..123..");
	}

	@Test
	public void supplierFirst() {
		assertThat(rest.getForObject("/not/a/function", String.class))
				.isEqualTo("[\"hello\"]");
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
						.isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	public void uppercaseSSE() throws Exception {
		assertThat(rest.exchange(RequestEntity.post(new URI("/uppercase"))
				.accept(EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class).getBody())
						.isEqualTo(sse("(FOO)", "(BAR)"));
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
					.map(value -> "(" + value.trim().toUpperCase() + ")");
		}

		@Bean
		public Function<String, String> bareUppercase() {
			return value -> "(" + value.trim().toUpperCase() + ")";
		}

		@Bean
		public Function<Flux<Foo>, Flux<Foo>> upFoos() {
			return flux -> flux.log()
					.map(value -> new Foo(value.getValue().trim().toUpperCase()));
		}

		@Bean
		public Function<Foo, Foo> bareUpFoos() {
			return value -> new Foo(value.getValue().trim().toUpperCase());
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
			return () -> Flux.just("foo", "bar");
		}

		@Bean
		public Supplier<Flux<Foo>> foos() {
			return () -> Flux.just(new Foo("foo"), new Foo("bar"));
		}

		@Bean
		@Qualifier("foos")
		public Function<String, Foo> qualifier() {
			return value -> new Foo("[" + value.trim().toUpperCase() + "]");
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
		public Consumer<Flux<Foo>> addFoos() {
			return flux -> flux.subscribe(value -> list.add(value.getValue()));
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

		@Bean("not/a/function")
		public Supplier<Flux<String>> supplier() {
			return () -> Flux.just("hello");
		}

		@Bean("not/a")
		public Function<Flux<String>, Flux<String>> function() {
			return input -> Flux.just("bye");
		}

		@Bean
		public Supplier<Flux<String>> timeout() {
			return () -> Flux.defer(() -> Flux.<String>create(emitter -> {
				emitter.next("foo");
			}).timeout(Duration.ofMillis(100L), Flux.empty()));
		}

		@Bean
		public Supplier<Flux<List<String>>> sentences() {
			return () -> Flux.just(Arrays.asList("go", "home"),
					Arrays.asList("come", "back"));
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
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

}
