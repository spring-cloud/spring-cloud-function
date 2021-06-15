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

package org.springframework.cloud.function.web.flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.flux.HttpPostIntegrationTests.ApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.main.web-application-type=reactive")
@ContextConfiguration(classes = { RestApplication.class, ApplicationConfiguration.class })
public class HttpPostIntegrationTests {

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

	@AfterEach
	public void done() {
		this.test.list.clear();
	}

	@Test
	@DirtiesContext
	public void qualifierFoos() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/foos")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"[FOO]\"},{\"value\":\"[BAR]\"}]");
	}

	@Test
	@DirtiesContext
	public void updates() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/updates")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"one\", \"two\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(this.test.list).hasSize(2);
		assertThat(result.getBody()).isNull();
	}

	@Test
	@DirtiesContext
	public void updatesJson() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/updates")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"one\",\"two\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(this.test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo(null);
	}

	@Test
	@DirtiesContext
	public void addFoos() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/addFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(this.test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo(null);
	}

	@Test
	@DirtiesContext
	public void addFoosFlux() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/addFoosFlux")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(this.test.list).hasSize(2);
		assertThat(result.getBody()).isEqualTo(null);
	}

	@Test
	@DirtiesContext
	public void bareUpdates() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/bareUpdates")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"one\",\"two\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(this.test.list).hasSize(2);
	}

	@Test
	@DirtiesContext
	public void uppercase() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/uppercase")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	@DirtiesContext
	public void messages() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/messages")).contentType(MediaType.APPLICATION_JSON)
				.header("x-foo", "bar").body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getHeaders().getFirst("x-foo")).isEqualTo("bar");
		assertThat(result.getHeaders()).doesNotContainKey("id");
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	@DirtiesContext
	public void headers() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/headers")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
//		assertThat(result.getHeaders().getFirst("foo")).isEqualTo("bar");
//		assertThat(result.getHeaders()).doesNotContainKey("id");
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	@DirtiesContext
	public void uppercaseSingleValue() throws Exception {
		ResponseEntity<String> result = this.rest
				.exchange(
						RequestEntity.post(new URI("/uppercase"))
								.contentType(MediaType.TEXT_PLAIN).body("foo"),
						String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\"]");
	}

	@Test
	@Disabled("WebFlux would split the request body into lines: TODO make this work the same")
	public void uppercasePlainText() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(
				RequestEntity.post(new URI("/uppercase"))
						.contentType(MediaType.TEXT_PLAIN).body("foo\nbar"),
				String.class);
		assertThat(result.getBody()).isEqualTo("(FOO\nBAR)");
	}

	@Test
	@DirtiesContext
	public void uppercaseFoos() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/upFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	@DirtiesContext
	public void uppercaseFoo() throws Exception {
		// Single Foo can be parsed
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/upFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("{\"value\":\"foo\"}"), String.class);
		assertThat(result.getBody()).isEqualTo("[{\"value\":\"FOO\"}]");
	}

	@Test
	@DirtiesContext
	public void bareUppercaseFoos() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/bareUpFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"), String.class);
		assertThat(result.getBody())
				.isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	@DirtiesContext
	@Disabled // not sure if this test is correct. Why does ? has to be assumed as String?
	public void typelessFunctionPassingArray() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(
				RequestEntity.post(new URI("/typelessFunctionExpectingText"))
						.contentType(MediaType.TEXT_PLAIN).body("[{\"value\":\"foo\"}]"),
				String.class);
		assertThat(result.getBody()).isEqualTo("[{\"value\":\"foo\"}]");
	}

	@Test
	@DirtiesContext
	public void bareUppercaseFoo() throws Exception {
		// Single Foo can be parsed and returns a single value if the function is defined
		// that way
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/bareUpFoos")).contentType(MediaType.APPLICATION_JSON)
				.body("{\"value\":\"foo\"}"), String.class);
		assertThat(result.getBody()).isEqualTo("{\"value\":\"FOO\"}");
	}

	@Test
	@DirtiesContext
	public void bareUppercase() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/bareUppercase")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	@DirtiesContext
	public void singleValuedText() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(
				RequestEntity.post(new URI("/bareUppercase")).accept(MediaType.TEXT_PLAIN)
						.contentType(MediaType.TEXT_PLAIN).body("foo"),
				String.class);
		assertThat(result.getBody()).isEqualTo("(FOO)");
	}

	@Test
	@DirtiesContext
	public void transform() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/transform")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	@DirtiesContext
	public void postMore() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(RequestEntity
				.post(new URI("/post/more")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getBody()).isEqualTo("[\"(FOO)\",\"(BAR)\"]");
	}

	@Test
	@DirtiesContext
	public void convertPost() throws Exception {
		ResponseEntity<String> result = this.rest
				.exchange(
						RequestEntity.post(new URI("/wrap"))
								.contentType(MediaType.TEXT_PLAIN).body("123"),
						String.class);
		// Result is multi-valued so it has to come out as an array
		assertThat(result.getBody()).isEqualTo("[\"..123..\"]");
	}

	@Test
	@DirtiesContext
	public void convertPostJson() throws Exception {
		// If you POST a single value to a Function<Flux<Integer>,Flux<Integer>> it can't
		// determine if the output is single valued, so it has to send an array back
		ResponseEntity<String> result = this.rest
				.exchange(
						RequestEntity.post(new URI("/doubler"))
								.contentType(MediaType.TEXT_PLAIN).body("123"),
						String.class);
		assertThat(result.getBody()).isEqualTo("[246]");
	}

	@Test
	@DirtiesContext
	public void uppercaseJsonArray() throws Exception {
		assertThat(this.rest.exchange(
				RequestEntity.post(new URI("/maps"))
						.contentType(MediaType.APPLICATION_JSON)
						// The new line in the middle is optional
						.body("[{\"value\":\"foo\"},\n{\"value\":\"bar\"}]"),
				String.class).getBody())
						.isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@Test
	@DirtiesContext
	public void uppercaseSSE() throws Exception {
		assertThat(this.rest.exchange(RequestEntity.post(new URI("/uppercase")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class).getBody())
						.isEqualTo(sse("(FOO)", "(BAR)"));
	}

	@Test
	@DirtiesContext
	public void sum() throws Exception {

		LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();

		map.put("A", Arrays.asList("1", "2", "3"));
		map.put("B", Arrays.asList("5", "6"));

		assertThat(this.rest.exchange(
				RequestEntity.post(new URI("/sum")).accept(MediaType.APPLICATION_JSON)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(map),
				String.class).getBody()).isEqualTo("{\"A\":6,\"B\":11}");
	}

	@Test
	@DirtiesContext
	public void multipart() throws Exception {

		LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();

		map.put("A", Arrays.asList("1", "2", "3"));
		map.put("B", Arrays.asList("5", "6"));

		assertThat(this.rest.exchange(
				RequestEntity.post(new URI("/sum")).accept(MediaType.APPLICATION_JSON)
						.contentType(MediaType.MULTIPART_FORM_DATA).body(map),
				String.class).getBody()).isEqualTo("{\"A\":6,\"B\":11}");
	}

	@Test
	@DirtiesContext
	public void count() throws Exception {
		List<String> list = Arrays.asList("A", "B", "A");
		assertThat(this.rest.exchange(
				RequestEntity.post(new URI("/count")).accept(MediaType.APPLICATION_JSON)
						.contentType(MediaType.APPLICATION_JSON).body(list),
				String.class).getBody()).isEqualTo("{\"A\":2,\"B\":1}");
	}

	@Test
	@DirtiesContext
	public void fluxWithList() throws Exception {
		List<String> list = Arrays.asList("A", "B", "A");
		assertThat(this.rest.exchange(
				RequestEntity.post(new URI("/fluxCollectionEcho")).accept(MediaType.APPLICATION_JSON)
						.contentType(MediaType.APPLICATION_JSON).body(list),
				String.class).getBody()).isEqualTo("[\"A\",\"B\",\"A\"]");
	}

	private String sse(String... values) {
		return "[\"" + StringUtils.arrayToDelimitedString(values, "\",\"") + "\"]";
	}

	@EnableAutoConfiguration
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
		public Function<?, ?> typelessFunctionExpectingText() {
			return value -> {
				Assert.isInstanceOf(String.class, value);
				return value;
			};
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
			return value -> {
				return new Foo("[" + value.trim().toUpperCase() + "]");
			};
		}

		@Bean
		public Consumer<Flux<String>> updates() {
			return flux -> flux.subscribe(value -> {
				System.out.println();
					this.list.add(value);
				});
		}

		@Bean
		public Consumer<Flux<Foo>> addFoosFlux() {
			return flux -> flux.subscribe(value -> this.list.add(value.getValue()));
		}

		@Bean
		public Consumer<Foo> addFoos() {
			return value -> {
				this.list.add(value.getValue());
			};
		}

		@Bean
		public Consumer<String> bareUpdates() {
			return value -> {
				this.list.add(value);
			};
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

		@Bean
		public Function<Flux<List<String>>, Flux<String>> fluxCollectionEcho() {
			return flux -> flux.flatMap(v -> Flux.fromIterable(v));
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
