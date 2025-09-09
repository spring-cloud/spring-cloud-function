/*
 * Copyright 2024-present the original author or authors.
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

package org.springframework.cloud.function.serverless.web;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Oleg Zhurakousky
 */
public class ServerlessWebServerFactoryTests {

	@Test
	public void testServerFactoryExists() {
		ServerlessMVC mvc = ServerlessMVC.INSTANCE(TestApplication.class);
		mvc.getApplicationContext();
	}

	@SpringBootApplication
	public static class TestApplication {

	}
}
