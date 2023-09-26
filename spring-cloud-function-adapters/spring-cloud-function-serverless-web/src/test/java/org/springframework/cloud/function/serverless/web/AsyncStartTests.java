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

package org.springframework.cloud.function.serverless.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Oleg Zhurakousky
 */
public class AsyncStartTests {

	@Test
	public void testAsync() throws Exception {
		long start = System.currentTimeMillis();
		ProxyMvc mvc = ProxyMvc.INSTANCE(SlowStartController.class);
		assertThat(System.currentTimeMillis() - start).isLessThan(2000);
		HttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/hello");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		assertThat(System.currentTimeMillis() - start).isGreaterThan(2000);
		assertThat(response.getContentAsString()).isEqualTo("hello");
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	public void testAsyncWithEnvSet() throws Exception {
		System.setProperty(ProxyMvc.INIT_TIMEOUT, "500");
		long start = System.currentTimeMillis();
		ProxyMvc mvc = ProxyMvc.INSTANCE(SlowStartController.class);
		assertThat(System.currentTimeMillis() - start).isLessThan(2000);
		HttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/hello");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		try {
			mvc.service(request, response);
			fail();
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(IllegalStateException.class);
			String message = e.getMessage();
			assertThat(message).startsWith("Failed to initialize Application within the specified time");
		}
	}

	@RestController
	@EnableWebMvc
	public static class SlowStartController {

		public SlowStartController() throws Exception {
			Thread.sleep(2000);
		}

		@RequestMapping(path = "/hello", method = RequestMethod.GET)
		public String hello() {
			return "hello";
		}
	}

}
