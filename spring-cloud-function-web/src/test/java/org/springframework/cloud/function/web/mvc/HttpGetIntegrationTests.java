/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.function.web.mvc;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.test.LocalServerPort;
import org.springframework.boot.web.server.test.client.TestRestTemplate;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.mvc.HttpGetIntegrationTests.ApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Chris Bono
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.main.web-application-type=servlet")
@ContextConfiguration(classes = { RestApplication.class, ApplicationConfiguration.class })
@DirtiesContext
public class HttpGetIntegrationTests {

	private static final MediaType EVENT_STREAM = MediaType.TEXT_EVENT_STREAM;

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ApplicationConfiguration test;

	@BeforeEach
	public void init() {
		this.test.list.clear();
	}

	@Test
	public void staticResource() {
		assertThat(this.rest.getForObject("/test.html", String.class))
				.contains("<body>Test");
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
	public void word() throws Exception {
		ResponseEntity<String> result = this.rest
				.exchange(RequestEntity.get(new URI("/word")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("foo");
	}

	@ParameterizedTest
	@ValueSource(strings = {"[hello", "hello]", "[hello]"})
	void textContentTypeWithValueWrappedBracketsIsOk(String inputMessagePayloadValue) throws URISyntaxException {
		ResponseEntity<String> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/echo"))
						.contentType(MediaType.TEXT_PLAIN)
						.body(inputMessagePayloadValue), String.class);
		assertThat(postForEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(postForEntity.getBody()).isEqualTo(inputMessagePayloadValue);
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
	public void bareWords() throws Exception {
		ResponseEntity<String> result = this.rest
				.exchange(RequestEntity.get(new URI("/bareWords")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	public void timeoutJson() throws Exception {
		assertThat(this.rest
				.exchange(RequestEntity.get(new URI("/timeout"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
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
	@Disabled
	public void sentencesAcceptSse() throws Exception {
		Thread.sleep(1000);
		ResponseEntity<String> result = this.rest.exchange(
				RequestEntity.get(new URI("/sentences")).accept(EVENT_STREAM).build(),
				String.class);
		assertThat(result.getBody())
				.isEqualTo(sse("[\"go\",\"home\"]", "[\"come\",\"back\"]"));
		assertThat(result.getHeaders().getContentType().isCompatibleWith(EVENT_STREAM))
				.isTrue();
	}

	@Test
	public void postMoreFoo() {
		assertThat(this.rest.getForObject("/post/more/foo", String.class))
				.isEqualTo("[\"(FOO)\"]");
	}

	@Test
	public void uppercaseGet() {
		assertThat(this.rest.getForObject("/uppercase/foo", String.class))
				.isEqualTo("[\"(FOO)\"]");
	}

	@Test
	public void convertGet() {
		assertThat(this.rest.getForObject("/wrap/123", String.class))
				.isEqualTo("[\"..123..\"]");
	}

	@Test
	public void supplierFirst() {
		assertThat(this.rest.getForObject("/not/a/function", String.class))
				.isEqualTo("[\"hello\"]");
	}

	@Test
	public void convertGetJson() throws Exception {
		assertThat(this.rest
				.exchange(RequestEntity.get(new URI("/entity/321"))
						.accept(MediaType.APPLICATION_JSON).build(), String.class)
				.getBody()).isEqualTo("[{\"value\":321}]");
	}

	@Test
	@Disabled
	// this test is wrong since it is returning Flux while setting CT to TEXT_PLAIN. We can't convert it
	public void compose() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.get(new URI("/concat,reverse/foo")).accept(MediaType.TEXT_PLAIN).build(),
				String.class);
		assertThat(result.getBody()).isEqualTo("oofoof");
	}

	private String sse(String... values) {
		return "data:" + StringUtils.arrayToDelimitedString(values, "\n\ndata:") + "\n\n";
	}

	@EnableAutoConfiguration
	@TestConfiguration
	public static class ApplicationConfiguration {

		private List<String> list = new ArrayList<>();

		public static void main(String[] args) throws Exception {
			SpringApplication.run(HttpGetIntegrationTests.ApplicationConfiguration.class,
					args);
		}

		@Bean
		public Function<Flux<String>, Flux<String>> concat() {
			return flux -> flux.map(v -> v + v);
		}

		@Bean
		public Function<Flux<String>, Flux<String>> reverse() {
			return flux -> flux.log()
					.map(value -> new StringBuilder(value.trim()).reverse().toString());
		}

		@Bean({ "uppercase", "post/more" })
		public Function<Flux<String>, Flux<String>> uppercase() {
			return flux -> flux.log()
					.map(value -> "(" + value.trim().toUpperCase(Locale.ROOT) + ")");
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

		@Bean({ "words", "get/more" })
		public Supplier<Flux<String>> words() {
			return () -> Flux.just("foo", "bar");
		}

		@Bean
		public Supplier<String> word() {
			return () -> "foo";
		}

		@Bean
		public Function<String, String> echo() {
			return (input) -> input;
		}

		@Bean
		public Supplier<Flux<Foo>> foos() {
			return () -> Flux.just(new Foo("foo"), new Foo("bar"));
		}

		@Bean
		public Supplier<List<String>> bareWords() {
			return () -> Arrays.asList("foo", "bar");
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
			}).timeout(Duration.ofMillis(1000L), Flux.empty()));
		}

		@Bean
		public Supplier<Flux<List<String>>> sentences() {
			return () -> Flux.just(Arrays.asList("go", "home"),
					Arrays.asList("come", "back"));
		}

		@Bean
		public Function<MultiValueMap<String, String>, Map<String, Integer>> sum() {
			return valueMap -> valueMap.entrySet().stream()
					.collect(Collectors.toMap(Map.Entry::getKey, values -> values
							.getValue().stream().mapToInt(Integer::parseInt).sum()));
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
