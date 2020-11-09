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

package org.springframework.cloud.function.web.mvc;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.SocketUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class MultipartFileTests {

	@BeforeEach
	public void init() throws Exception {
		String port = "" + SocketUtils.findAvailableTcpPort();
		System.setProperty("server.port", port);
	}

	@AfterEach
	public void close() throws Exception {
		System.clearProperty("server.port");
	}

	@Test
	public void testMultipartFileUpload() throws Exception {
		ApplicationContext context = SpringApplication.run(TestConfiguration.class);
		JsonMapper mapper = context.getBean(JsonMapper.class);
		TestRestTemplate template = new TestRestTemplate();
		String port = System.getProperty("server.port");

		LinkedMultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
		map.add("file", new ClassPathResource("META-INF/spring.factories"));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<LinkedMultiValueMap<String, Object>>(
				map, headers);
		ResponseEntity<String> result = template.exchange(new URI("http://localhost:" + port + "/uppercase"),
				HttpMethod.POST, requestEntity, String.class);
		List<String> resultCollection = mapper.fromJson(result.getBody(), List.class);
		assertThat(resultCollection.get(0)).isEqualTo("SPRING.FACTORIES");
	}

	@Test
	public void testMultipartFilesUpload() throws Exception {
		ApplicationContext context = SpringApplication.run(TestConfiguration.class);
		JsonMapper mapper = context.getBean(JsonMapper.class);
		TestRestTemplate template = new TestRestTemplate();
		String port = System.getProperty("server.port");

		LinkedMultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
		map.add("fileA", new ClassPathResource("META-INF/spring.factories"));
		map.add("fileB", new ClassPathResource("static/test.html"));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<LinkedMultiValueMap<String, Object>>(
				map, headers);
		ResponseEntity<String> result = template.exchange(new URI("http://localhost:" + port + "/uppercase"),
				HttpMethod.POST, requestEntity, String.class);
		List<String> resultCollection = mapper.fromJson(result.getBody(), List.class);
		assertThat(resultCollection.get(0)).isEqualTo("SPRING.FACTORIES");
		assertThat(resultCollection.get(1)).isEqualTo("TEST.HTML");
	}

	@EnableAutoConfiguration
	protected static class TestConfiguration {

		@Bean
		public Function<MultipartFile, String> uppercase() {
			return value -> {
				return value.getOriginalFilename().toUpperCase();
			};
		}
	}
}
