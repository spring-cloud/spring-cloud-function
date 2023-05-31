/*
 * Copyright 2023-2023 the original author or authors.
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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
/**
 *
 * @author Oleg Zhurakousky
 */
public class GeneralIntegrationTests {

	@Test
	public void testMappedAndUnmappedDeleteFunction() throws Exception {
		ApplicationContext context = SpringApplication.run(MultipleConsumerConfiguration.class, "--server.port=0", "--spring.cloud.function.http.DELETE=delete2");
		String port = context.getEnvironment().getProperty("local.server.port");
		JsonMapper mapper = context.getBean(JsonMapper.class);
		TestRestTemplate template = new TestRestTemplate();

		ResponseEntity<Void> result = template.exchange(
				RequestEntity.delete(new URI("http://localhost:" + port + "/delete1"))
				.build(), Void.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

		result = template.exchange(
				RequestEntity.delete(new URI("http://localhost:" + port + "/delete2"))
				.build(), Void.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}


	@EnableAutoConfiguration
	protected static class MultipleConsumerConfiguration {

		@Bean
		public Consumer<String> delete1() {
			return v -> {};
		}

		@Bean
		public Consumer<String> delete2() {
			return v -> {};
		}
	}
}
