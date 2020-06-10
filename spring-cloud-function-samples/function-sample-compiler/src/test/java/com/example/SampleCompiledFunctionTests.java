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

package com.example;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mark Fisher
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
	"spring.cloud.function.compile.test.lambda=f->f.map(s->s+\"!!!\")",
	"spring.cloud.function.compile.test.inputType=Flux<String>",
	"spring.cloud.function.compile.test.outputType=Flux<String>"})
public class SampleCompiledFunctionTests {

	@LocalServerPort
	private int port;

	@Test
	public void lowercase() {
		assertThat(new TestRestTemplate().postForObject(
			"http://localhost:" + this.port + "/test", "it works", String.class))
			.contains("it works!!!");
	}

}
