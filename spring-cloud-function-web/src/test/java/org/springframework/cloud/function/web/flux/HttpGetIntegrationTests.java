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
package org.springframework.cloud.function.web.flux;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.flux.HttpGetIntegrationTests.ApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.main.web-application-type=reactive")
@ContextConfiguration(classes= {RestApplication.class, ApplicationConfiguration.class})
public class HttpGetIntegrationTests {

	private static final MediaType EVENT_STREAM = MediaType.TEXT_EVENT_STREAM;
	@LocalServerPort
	private int port;
	@Autowired
	private TestRestTemplate rest;
	@Autowired
	private ApplicationConfiguration test;

	@Before
	public void init() {
		test.list.clear();
	}

	@Test
	public void staticResource() {
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
	public void word() throws Exception {
		ResponseEntity<String> result = rest.exchange(
				RequestEntity.get(new URI("/word")).accept(MediaType.TEXT_PLAIN).build(),
				String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("foo");
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
	public void postMoreFoo() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.get(new URI("/post/more/foo")).accept(MediaType.TEXT_PLAIN).build(),
				String.class);
		assertThat(result.getBody()).isEqualTo("(FOO)");
	}

	@Test
	public void uppercaseGet() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.get(new URI("/uppercase/foo")).accept(MediaType.TEXT_PLAIN).build(),
				String.class);
		assertThat(result.getBody()).isEqualTo("(FOO)");
	}

	@Test
	public void convertGet() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.get(new URI("/wrap/123")).accept(MediaType.TEXT_PLAIN).build(),
				String.class);
		assertThat(result.getBody()).isEqualTo("..123..");
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

		@Bean({ "uppercase", "post/more" })
		public Function<Flux<String>, Flux<String>> uppercase() {
			return flux -> flux.log()
					.map(value -> "(" + value.trim().toUpperCase() + ")");
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
			}).timeout(Duration.ofMillis(100L), Flux.empty()));
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
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

}
