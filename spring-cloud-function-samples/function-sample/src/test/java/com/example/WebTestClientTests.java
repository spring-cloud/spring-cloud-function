package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@FunctionalSpringBootTest
@AutoConfigureWebTestClient
public class WebTestClientTests {

	@Autowired
	private WebTestClient client;

	@Test
	public void words() {
		client.get().uri("/words").exchange()
			.expectStatus().isOk().expectBody(String.class).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	public void uppercase() {
		client.post().uri("/uppercase").body(Mono.just("foo"), String.class).exchange()
				.expectStatus().isOk().expectBody(String.class).isEqualTo("FOO");
	}

	@Test
	public void lowercase() {
		client.post().uri("/lowercase").body(Flux.just("FOO", "BAR"), String.class).exchange()
				.expectStatus().isOk().expectBody(String.class).isEqualTo("[\"foobar\"]");
	}

	@Test
	public void lowercaseMulti() {
		client.post().uri("/lowercase").contentType(MediaType.APPLICATION_JSON).body(Mono.just("[\"FOO\"]"), String.class).exchange()
			.expectStatus().isOk().expectBody(String.class).isEqualTo("[\"foo\"]");
	}

	@Test
	public void testStream() {

		List<String> asObjectExpect = new ArrayList<>();
		asObjectExpect.add("foobar");

		//as object
		client.post().uri("/lowercase").accept(MediaType.TEXT_EVENT_STREAM).body(Flux.just("FOO", "BAR"), String.class)
				.exchange().expectBodyList(String.class).isEqualTo(asObjectExpect);

		List<String> asFluxExpect = new ArrayList<>();
		asFluxExpect.add("foo");
		asFluxExpect.add("bar");

		//as flux
		client.post().uri("/lowercase").accept(MediaType.TEXT_EVENT_STREAM).body(Flux.just("FOO\n", "BAR\n"), String.class)
			.exchange().expectBodyList(String.class).isEqualTo(asFluxExpect);
	}

	@Test
	public void testCollection() {
		client.post().uri("/lowercase").contentType(MediaType.APPLICATION_JSON).body(Mono.just("[\"FOO\",  \"BAR\"]"), String.class)
				.exchange().expectBody(String.class).isEqualTo("[\"foo\",\"bar\"]");
	}

}
