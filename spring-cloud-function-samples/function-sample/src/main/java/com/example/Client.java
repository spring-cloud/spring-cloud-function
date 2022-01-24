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

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Sample client to test infinite stream from function.
 *
 * @author Oleg Zhurakousky
 *
 */
public class Client {

	public static void main(String[] args) throws Exception {
		WebClient client = WebClient.create();
		WebClient.ResponseSpec responseSpec = client.post()
			    .uri("http://localhost:8080/infinite")
			    .header("accept", "text/event-stream")
			    .retrieve();

		responseSpec.bodyToFlux(String.class).subscribe(v -> {
			System.out.println(v);
		});

		System.in.read();

	}

}
