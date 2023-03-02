package org.springframework.cloud.function.adapter.aws.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ProxyHttpServletRequest;
import org.springframework.web.client.ProxyHttpServletResponse;
import org.springframework.web.client.ProxyMvc;
import org.springframework.web.client.WebProxyUtils;

import com.fasterxml.jackson.core.type.ResolvedType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RequestResponseTests {

	private ObjectMapper mapper = new ObjectMapper();

	private ProxyMvc mvc;

	@BeforeEach
	public void before() {
		this.mvc = WebProxyUtils.buildMvcProxy(PetStoreSpringAppConfig.class);
	}

	@AfterEach
	public void after() {
		this.mvc.stop();
	}

	@Test
	public void validateGetListOfPojos() throws Exception {
		HttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/pets");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		TypeReference<List<Pet>> tr = new TypeReference<List<Pet>>() {
		};
		List<Pet> pets = mapper.readValue(response.getContentAsByteArray(), tr);
		assertThat(pets.size()).isEqualTo(10);
		assertThat(pets.get(0)).isInstanceOf(Pet.class);
	}

	@Test
	public void validateGetPojo() throws Exception {
		HttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/pets/6e3cc370-892f-4efe-a9eb-82926ff8cc5b");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		Pet pet = mapper.readValue(response.getContentAsByteArray(), Pet.class);
		assertThat(pet).isNotNull();
		assertThat(pet.getName()).isNotEmpty();
	}

	@Test
	public void validatePost() throws Exception {
		ProxyHttpServletRequest request = new ProxyHttpServletRequest(null, "POST", "/pets/");
		String jsonPet = "{\n"
				+ "   \"id\":\"1234\",\n"
				+ "   \"breed\":\"Canish\",\n"
				+ "   \"name\":\"Foo\",\n"
				+ "   \"date\":\"2012-04-23T18:25:43.511Z\"\n"
				+ "}";
		request.setContent(jsonPet.getBytes());
		request.setContentType("application/json");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		Pet pet = mapper.readValue(response.getContentAsByteArray(), Pet.class);
		assertThat(pet).isNotNull();
		assertThat(pet.getName()).isNotEmpty();
	}

}
