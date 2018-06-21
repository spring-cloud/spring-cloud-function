/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.cloud.function.scan;

import java.net.URI;
import java.util.function.Function;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ComponentTests {

	@LocalServerPort
	private int port;
	@Autowired
	private Greeter greeter;
	@Autowired
	private TestRestTemplate rest;

	@Test
	public void contextLoads() throws Exception {
		assertThat(greeter).isNotNull();
	}

	@Test
	@Ignore("FIXME")
	public void greeter() throws Exception {
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("/greeter"))
								.contentType(MediaType.TEXT_PLAIN).body("World"),
						String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("Hello World");
	}

	@SpringBootApplication
	@ComponentScan
	protected static class TestConfiguration {
	}

	@Component("greeter")
	protected static class Greeter implements Function<Flux<String>, Flux<String>> {
		@Override
		public Flux<String> apply(Flux<String> flux) {
			return flux.map(name -> "Hello " + name);
		}
	}

}
