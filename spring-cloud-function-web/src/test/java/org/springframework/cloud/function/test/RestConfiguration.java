/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.function.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
@RestController
public class RestConfiguration {

	private static Log logger = LogFactory.getLog(RestConfiguration.class);

	List<String> inputs = new ArrayList<>();

	private Iterator<String> outputs = Arrays.asList("hello", "world").iterator();

	@GetMapping("/")
	ResponseEntity<String> home() {
		logger.info("HOME");
		if (this.outputs.hasNext()) {
			return ResponseEntity.ok(this.outputs.next());
		}
		return ResponseEntity.notFound().build();
	}

	@PostMapping("/")
	ResponseEntity<String> accept(@RequestBody String body) {
		logger.info("ACCEPT");
		this.inputs.add(body);
		return ResponseEntity.accepted().body(body);
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(RestConfiguration.class, args);
	}

}
