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
package org.springframework.cloud.function.deployer;

import java.net.URI;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class FunctionExtractingFunctionCatalogIntegrationTests {

	private static ConfigurableApplicationContext context;
	private static int port;

	@BeforeClass
	public static void open() throws Exception {
		port = SocketUtils.findAvailableTcpPort();
		// System.setProperty("debug", "true");
		context = new ApplicationRunner().start("--server.port=" + port, "--debug",
				"--logging.level.org.springframework.cloud.function=DEBUG");
		deploy("sample", "maven://io.spring.sample:function-sample-pojo:1.0.0.M3");
	}

	private static void deploy(String name, String path) throws Exception {
		ResponseEntity<String> result = new TestRestTemplate().postForEntity(
				"http://localhost:" + port + "/admin/" + name + "?path=" + path, "",
				String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private static String undeploy(String name) throws Exception {
		ResponseEntity<String> result = new TestRestTemplate().exchange(RequestEntity
				.delete(new URI("http://localhost:" + port + "/admin/" + name)).build(),
				String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		return result.getBody();
	}

	@AfterClass
	public static void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void listing() {
		assertThat(new TestRestTemplate()
				.getForObject("http://localhost:" + port + "/admin", String.class))
						.startsWith("{").contains("sample");
	}

	@Test
	public void words() {
		assertThat(new TestRestTemplate().getForObject(
				"http://localhost:" + port + "/stream/sample/words", String.class))
						.isEqualTo("[{\"value\":\"foo\"},{\"value\":\"bar\"}]");
	}

	@Test
	public void missing() throws Exception {
		ResponseEntity<String> result = new TestRestTemplate().exchange(RequestEntity
				.get(new URI("http://localhost:" + port + "/stream/missing/words"))
				.build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	public void uppercase() {
		assertThat(new TestRestTemplate().postForObject(
				"http://localhost:" + port + "/stream/sample/uppercase",
				"{\"value\":\"foo\"}", String.class)).isEqualTo("{\"value\":\"FOO\"}");
	}

	@Test
	public void another() throws Exception {
		deploy("strings", "maven://io.spring.sample:function-sample:1.0.0.M3");
		assertThat(new TestRestTemplate().getForObject(
				"http://localhost:" + port + "/stream/strings/words", String.class))
						.isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	public void cycle() throws Exception {
		String undeploy = undeploy("sample");
		assertThat(undeploy.contains("\"name\":\"sample\""));
		assertThat(undeploy.contains(
				"\"path\":\"maven://io.spring.sample:function-sample-pojo:1.0.0.M3\""));
		ResponseEntity<String> result = new TestRestTemplate().exchange(RequestEntity
				.get(new URI("http://localhost:" + port + "/stream/sample/words"))
				.build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		deploy("sample", "maven://io.spring.sample:function-sample-pojo:1.0.0.M3");
		assertThat(new TestRestTemplate().postForObject(
				"http://localhost:" + port + "/stream/sample/uppercase",
				"{\"value\":\"foo\"}", String.class)).isEqualTo("{\"value\":\"FOO\"}");
	}

}
