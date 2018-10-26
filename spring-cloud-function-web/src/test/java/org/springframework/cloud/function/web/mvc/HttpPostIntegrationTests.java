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
package org.springframework.cloud.function.web.mvc;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.mvc.HttpPostIntegrationTests.ApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties="spring.main.web-application-type=servlet")
@ContextConfiguration(classes= {RestApplication.class, ApplicationConfiguration.class})
public class HttpPostIntegrationTests {

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
	public void qualifierFoos() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity.post(new URI("/foos"))
				.contentType(MediaType.APPLICATION_JSON).body("[\"foo\",\"bar\"]"),
				String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"[FOO]\"},{\"value\":\"[BAR]\"}]");
	}

	@Test
	public void updates() throws Exception {
		ResponseEntity<String> result = rest.exchange(
				RequestEntity.post(new URI("/updates")).body("[\"one\", \"two\"]"),
				String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody()).isNull();
	}

	@Test
	public void updatesJson() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/updates")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"one\",\"two\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo(null);
	}

	@Test
	public void addFoos() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/addFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody())
				.isEqualTo(null);
	}

	@Test
	public void bareUpdates() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/bareUpdates")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"one\",\"two\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo("[]");
	}

	@Test
	public void uppercase() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/uppercase")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	public void messages() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/messages")).contentType(MediaType.APPLICATION_JSON)
				// Remove this when Spring 5.0.8 is used
				.accept(MediaType.valueOf("application/stream+json"))
				.header("x-foo", "bar").body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getHeaders().getFirst("x-foo")).isEqualTo("bar");
		assertThat(result.getHeaders()).doesNotContainKey("id");
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	public void headers() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/headers")).contentType(MediaType.APPLICATION_JSON)
				// Remove this when Spring 5.0.8 is used
				.accept(MediaType.valueOf("application/stream+json"))
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getHeaders().getFirst("foo")).isEqualTo("bar");
		assertThat(result.getHeaders()).doesNotContainKey("id");
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	public void uppercaseSingleValue() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("/uppercase"))
								.contentType(MediaType.TEXT_PLAIN).body("foo"),
						String.class);
		// Result is multi-valued so it has to come out as an array
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\"]");
	}

	@Test
	@Ignore("WebFlux would split the request body into lines: TODO make this work the same")
	public void uppercasePlainText() throws Exception {
		ResponseEntity<String> result = rest.exchange(
				RequestEntity.post(new URI("/uppercase"))
						.contentType(MediaType.TEXT_PLAIN).body("foo\nbar"),
				String.class);
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
		assertThat(result.getBody()).isEqualTo("[{\"value\":\"FOO\"}]");
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
		// Single Foo can be parsed and returns a single value if the function is defined
		// that way
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/bareUpFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("{\"value\":\"foo\"}"), String.class);
		assertThat(result.getBody()).isEqualTo("{\"value\":\"FOO\"}");
	}

	@Test
	public void bareUppercase() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity
				.post(new URI("/bareUppercase")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	public void singleValuedText() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("/bareUppercase"))
								.contentType(MediaType.TEXT_PLAIN).body("foo"),
						String.class);
		assertThat(result.getBody()).isEqualTo("(FOO)");
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
	public void convertPost() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity.post(new URI("/wrap"))
				.contentType(MediaType.TEXT_PLAIN).body("123"), String.class);
		// Result is multi-valued so it has to come out as an array
		assertThat(result.getBody()).isEqualTo("[\"..123..\"]");
	}

	@Test
	public void convertPostJson() throws Exception {
		// If you POST a single value to a Function<Flux<Integer>,Flux<Integer>> it can't
		// determine if the output is single valued, so it has to send an array back
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("/doubler"))
								.contentType(MediaType.TEXT_PLAIN).body("123"),
						String.class);
		assertThat(result.getBody()).isEqualTo("[246]");
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

	@Test
	public void sum() throws Exception {

		LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();

		map.put("A", Arrays.asList("1", "2", "3"));
		map.put("B", Arrays.asList("5", "6"));

		assertThat(rest.exchange(
				RequestEntity.post(new URI("/sum")).accept(MediaType.APPLICATION_JSON)
						.contentType(MediaType.MULTIPART_FORM_DATA).body(map),
				String.class).getBody()).isEqualTo("[{\"A\":6,\"B\":11}]");
	}

	@Test
	public void multipart() throws Exception {

		LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();

		map.put("A", Arrays.asList("1", "2", "3"));
		map.put("B", Arrays.asList("5", "6"));

		assertThat(rest.exchange(
				RequestEntity.post(new URI("/sum")).accept(MediaType.APPLICATION_JSON)
						.contentType(MediaType.MULTIPART_FORM_DATA).body(map),
				String.class).getBody()).isEqualTo("[{\"A\":6,\"B\":11}]");
	}

	@Test
	public void count() throws Exception {
		List<String> list = Arrays.asList("A", "B", "A");
		assertThat(rest.exchange(
				RequestEntity.post(new URI("/count")).accept(MediaType.APPLICATION_JSON)
						.contentType(MediaType.APPLICATION_JSON).body(list),
				String.class).getBody()).isEqualTo("{\"A\":2,\"B\":1}");
	}

	private String sse(String... values) {
		return "data:" + StringUtils.arrayToDelimitedString(values, "\n\ndata:") + "\n\n";
	}

	@EnableAutoConfiguration
	@TestConfiguration
	public static class ApplicationConfiguration {

		private List<String> list = new ArrayList<>();

		public static void main(String[] args) throws Exception {
			SpringApplication.run(HttpPostIntegrationTests.ApplicationConfiguration.class,
					args);
		}

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
		public Function<Message<String>, Message<String>> messages() {
			return value -> MessageBuilder
					.withPayload("(" + value.getPayload().trim().toUpperCase() + ")")
					.copyHeaders(value.getHeaders()).build();
		}

		@Bean
		public Function<Flux<Message<String>>, Flux<Message<String>>> headers() {
			return flux -> flux.map(value -> MessageBuilder
					.withPayload("(" + value.getPayload().trim().toUpperCase() + ")")
					.setHeader("foo", "bar").build());
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
		public Function<Flux<Integer>, Flux<Integer>> doubler() {
			return flux -> flux.log().map(value -> 2 * value);
		}

		@Bean
		public Function<Flux<HashMap<String, String>>, Flux<Map<String, String>>> maps() {
			return flux -> flux.map(value -> {
				value.put("value", value.get("value").trim().toUpperCase());
				return value;
			});
		}

		@Bean
		@Qualifier("foos")
		public Function<String, Foo> qualifier() {
			return value -> new Foo("[" + value.trim().toUpperCase() + "]");
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

		@Bean("not/a")
		public Function<Flux<String>, Flux<String>> function() {
			return input -> Flux.just("bye");
		}

		@Bean
		public Function<MultiValueMap<String, String>, Map<String, Integer>> sum() {
			return valueMap -> valueMap.entrySet().stream()
					.collect(Collectors.toMap(Map.Entry::getKey, values -> values
							.getValue().stream().mapToInt(Integer::parseInt).sum()));
		}

		@Bean
		public Function<Flux<String>, Mono<Map<String, Integer>>> count() {
			return flux -> flux.collect(HashMap::new,
					(map, word) -> map.merge(word, 1, Integer::sum));
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
