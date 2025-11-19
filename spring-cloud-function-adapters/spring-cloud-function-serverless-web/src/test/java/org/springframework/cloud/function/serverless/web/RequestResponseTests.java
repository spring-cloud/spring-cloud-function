/*
 * Copyright 2023-present the original author or authors.
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

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.cloud.function.test.app.Pet;
import org.springframework.cloud.function.test.app.PetStoreSpringAppConfig;
import org.springframework.cloud.function.test.app.PetStoreSpringAppConfig.AnotherFilter;
import org.springframework.cloud.function.test.app.PetStoreSpringAppConfig.SimpleFilter;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 *
 */
public class RequestResponseTests {

	private ObjectMapper mapper = new ObjectMapper();

	private ServerlessMVC mvc;

	@BeforeEach
	public void before() {
		System.setProperty("spring.main.banner-mode", "off");
		System.setProperty("trace", "true");
		System.setProperty("contextInitTimeout", "20000");
		this.mvc = ServerlessMVC.INSTANCE(PetStoreSpringAppConfig.class);
	}

	@AfterEach
	public void after() {
		this.mvc.stop();
	}

	@Test
	public void validateCaseInsensitiveHeaders() throws Exception {
		ServerlessHttpServletRequest request = new ServerlessHttpServletRequest(null, "GET", "/index");
		request.setHeader("User-Agent", "iOS");
		request.setHeader("uSer-Agent", "FOO");
		request.setContentType("application/json");
		request.setHeader("CoNteNt-tYpe", "text/plain");

		assertThat(request.getHeader("content-TYPE")).isEqualTo("application/json");
		assertThat(request.getHeader("user-agenT")).isEqualTo("iOS");
	}

	@Test
	public void validateFreemarker() throws Exception {
		HttpServletRequest request = new ServerlessHttpServletRequest(null, "GET", "/index");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		mvc.service(request, response);
		assertThat(response.getContentAsString()).contains("<h1> hello from freemarker </h1>");
	}

	@Test
	public void validateAccessDeniedWithCustomHandler() throws Exception {
		HttpServletRequest request = new ServerlessHttpServletRequest(null, "GET", "/foo/deny");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		mvc.service(request, response);
		assertThat(response.getErrorMessage()).isEqualTo("Can't touch this");
		assertThat(response.getStatus()).isEqualTo(403);
		SimpleFilter simpleFilter = this.mvc.getApplicationContext().getBean(SimpleFilter.class);
		assertThat(simpleFilter.invoked).isTrue();
		AnotherFilter anotherFilter = this.mvc.getApplicationContext().getBean(AnotherFilter.class);
		assertThat(anotherFilter.invoked).isTrue();
	}

	@Test
	public void validateGetListOfPojos() throws Exception {
		HttpServletRequest request = new ServerlessHttpServletRequest(null, "GET", "/pets");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		mvc.service(request, response);
		TypeReference<List<Pet>> tr = new TypeReference<List<Pet>>() {
		};
		List<Pet> pets = mapper.readValue(response.getContentAsByteArray(), tr);
		assertThat(pets.size()).isEqualTo(10);
		assertThat(pets.get(0)).isInstanceOf(Pet.class);
	}

	@Test
	public void validateGetListOfPojosWithParam() throws Exception {
		ServerlessHttpServletRequest request = new ServerlessHttpServletRequest(null, "GET", "/pets");
		request.setParameter("limit", "5");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		mvc.service(request, response);
		TypeReference<List<Pet>> tr = new TypeReference<List<Pet>>() {
		};
		List<Pet> pets = mapper.readValue(response.getContentAsByteArray(), tr);
		assertThat(pets.size()).isEqualTo(5);
		assertThat(pets.get(0)).isInstanceOf(Pet.class);
	}

	// @WithMockUser("spring")
	@Test
	public void validateGetPojo() throws Exception {
		HttpServletRequest request = new ServerlessHttpServletRequest(null, "GET",
				"/pets/6e3cc370-892f-4efe-a9eb-82926ff8cc5b");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		mvc.service(request, response);
		Pet pet = mapper.readValue(response.getContentAsByteArray(), Pet.class);
		assertThat(pet).isNotNull();
		assertThat(pet.getName()).isNotEmpty();
	}

	@Test
	public void errorThrownFromMethod() throws Exception {
		HttpServletRequest request = new ServerlessHttpServletRequest(null, "GET", "/pets/2");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		mvc.service(request, response);
		assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
		assertThat(response.getErrorMessage()).isEqualTo("No such Dog");
	}

	@Test
	public void errorUnexpectedWhitelabel() throws Exception {
		HttpServletRequest request = new ServerlessHttpServletRequest(null, "GET", "/pets/2/3/4");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		mvc.service(request, response);
		assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
	}

	@Test
	public void validatePostWithBody() throws Exception {
		ServerlessHttpServletRequest request = new ServerlessHttpServletRequest(null, "POST", "/pets/");
		String jsonPet = """
				{
				   "id":"1234",
				   "breed":"Canish",
				   "name":"Foo",
				   "date":"2012-04-23T18:25:43.511Z"
				}""";
		request.setContent(jsonPet.getBytes());
		request.setContentType("application/json");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		mvc.service(request, response);
		Pet pet = mapper.readValue(response.getContentAsByteArray(), Pet.class);
		assertThat(pet).isNotNull();
		assertThat(pet.getName()).isNotEmpty();
	}

	@Test
	public void validatePostWithoutBody() throws Exception {
		ServerlessHttpServletRequest request = new ServerlessHttpServletRequest(null, "POST", "/pets/");
		request.setContentType("application/json");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		try {
			mvc.service(request, response);
		}
		catch (jakarta.servlet.ServletException e) {
			assertThat(e.getCause()).isNotInstanceOf(NullPointerException.class);
		}

		assertThat(response.getStatus()).isEqualTo(400); // application fail because the
															// pet is empty ;)
	}

	@Test
	public void validatePostAsyncWithBody() throws Exception {
		// System.setProperty("spring.main.banner-mode", "off");
		ServerlessHttpServletRequest request = new ServerlessHttpServletRequest(null, "POST", "/petsAsync/");
		String jsonPet = """
				{
				   "id":"1234",
				   "breed":"Canish",
				   "name":"Foo",
				   "date":"2012-04-23T18:25:43.511Z"
				}""";
		request.setContent(jsonPet.getBytes());
		request.setContentType("application/json");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();
		mvc.service(request, response);
		Pet pet = mapper.readValue(response.getContentAsByteArray(), Pet.class);
		assertThat(pet).isNotNull();
		assertThat(pet.getName()).isNotEmpty();
	}

}
