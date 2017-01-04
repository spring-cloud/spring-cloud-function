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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
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
	public static void open() {
		port = SocketUtils.findAvailableTcpPort();
		// System.setProperty("debug", "true");
		context = new ApplicationRunner().start("--server.port=" + port);
	}

	@AfterClass
	public static void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void words() {
		assertThat(new TestRestTemplate()
				.getForObject("http://localhost:" + port + "/words", String.class))
						.isEqualTo("{\"value\":\"foo\"}{\"value\":\"bar\"}");
	}

	@Test
	public void uppercase() {
		assertThat(new TestRestTemplate().postForObject(
				"http://localhost:" + port + "/uppercase", "{\"value\":\"foo\"}",
				String.class)).isEqualTo("{\"value\":\"FOO\"}");
	}

}
