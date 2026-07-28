/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.function.web;

import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for POJO function invocation over HTTP, specifically verifying that
 * JSON deserialization failures produce descriptive Jackson errors rather than a
 * misleading {@link ClassCastException}.
 * <p>
 * <b>Architectural defect under test:</b><br>
 * When a POST request contains a JSON body whose structure does not match the function's
 * input POJO (e.g. an array where a String is expected), the framework previously
 * swallowed the Jackson deserialization failure and silently passed the raw JSON string
 * as the function argument. The function lambda then failed with a misleading
 * {@code ClassCastException: class java.lang.String cannot be cast to Person}. The true
 * cause (structural type mismatch in the JSON) was hidden behind an irrelevant cast error.
 * <p>
 * <b>Fix location:</b> {@code SimpleFunctionRegistry.convertInputIfNecessary()} in
 * {@code spring-cloud-function-context}. The fallback path now uses
 * {@code org.springframework.cloud.function.json.JsonMapper#isJsonString(Object)}
 * together with a Content-Type check to detect JSON payloads and dispatches through
 * {@code convertNonMessageInputIfNecessary} with
 * {@code maybeJson = true} when {@code convertInputMessageIfNecessary} returns
 * {@code null}. This causes Jackson to re-attempt deserialization and throw a descriptive
 * {@code tools.jackson.databind.exc.MismatchedInputException} that identifies the exact
 * field and type conflict.
 * <p>
 * These tests verify that correct input, structurally malformed JSON, completely
 * broken JSON, and scalar-typed JSON all produce the Jackson error instead of a
 * {@code ClassCastException}.
 * @author Roman Akentev
 *
 * @see org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry
 * @see org.springframework.cloud.function.json.JsonMapper#isJsonString(Object)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = { RestApplication.class, PojoFunctionIntegrationTests.TestConfig.class })
@AutoConfigureTestRestTemplate
@ExtendWith(OutputCaptureExtension.class)
class PojoFunctionIntegrationTests {

	@Autowired
	private TestRestTemplate rest;

	/**
	 * Verifies that a correctly structured JSON body is deserialized into the
	 * {@link Person} record and the function produces the expected output.
	 */
	@Test
	void testCorrectInput() {
		String json = """
				{"firstName":"john","lastName":"doe"}""";

		RequestEntity<String> request = RequestEntity
				.post("/personUppercase")
				.contentType(MediaType.APPLICATION_JSON)
				.body(json);

		ResponseEntity<String> result = this.rest.exchange(request, String.class);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("HELLO, JOHN DOE!");
	}

	/**
	 * Regression test: structurally valid JSON with wrong field types is caught
	 * by Jackson and produces a {@code MismatchedInputException} in the log
	 * instead of a silent {@code ClassCastException}.
	 */
	@Test
	void testMalformedJsonRejectsWithJacksonError(CapturedOutput output) {
		String json = """
				{"firstName": ["john"], "lastName": {"value": "doe"}}""";

		RequestEntity<String> request = RequestEntity
				.post("/personUppercase")
				.contentType(MediaType.APPLICATION_JSON)
				.body(json);

		ResponseEntity<String> result = this.rest.exchange(request, String.class);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(output)
				.as("Server log must contain Jackson deserialization error, not ClassCastException")
				.contains("MismatchedInputException")
				.contains("Cannot deserialize value");

		assertThat(output)
				.as("Server log must NOT contain the old misleading ClassCastException")
				.doesNotContain("ClassCastException");
	}

	/**
	 * Regression test: completely broken JSON (not syntactically valid) is caught
	 * by Jackson and produces a parse error in the log instead of a
	 * {@code ClassCastException}.
	 */
	@Test
	void testBrokenJsonRejectsWithJacksonError(CapturedOutput output) {
		String json = "{broken}";

		RequestEntity<String> request = RequestEntity
				.post("/personUppercase")
				.contentType(MediaType.APPLICATION_JSON)
				.body(json);

		ResponseEntity<String> result = this.rest.exchange(request, String.class);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(output)
				.as("Server log must contain Jackson parse error, not ClassCastException")
				.contains("Unexpected character");

		assertThat(output)
				.as("Server log must NOT contain the old misleading ClassCastException")
				.doesNotContain("ClassCastException");
	}

	/**
	 * Regression test: a JSON scalar string sent to a POJO function is caught
	 * by Jackson and produces a {@code MismatchedInputException} in the log.
	 * Without the fix the scalar passes through unchecked and the function
	 * lambda fails with a misleading {@code ClassCastException}.
	 */
	@Test
	void testScalarStringRejectsWithJacksonError(CapturedOutput output) {
		String json = "\"hello\"";

		RequestEntity<String> request = RequestEntity
				.post("/personUppercase")
				.contentType(MediaType.APPLICATION_JSON)
				.body(json);

		ResponseEntity<String> result = this.rest.exchange(request, String.class);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(output)
				.as("Server log must contain Jackson deserialization error, not ClassCastException")
				.contains("MismatchedInputException");

		assertThat(output)
				.as("Server log must NOT contain the old misleading ClassCastException")
				.doesNotContain("ClassCastException");
	}

	record Person(String firstName, String lastName) { }

	@Configuration
	static class TestConfig {

		@Bean
		public Function<Person, String> personUppercase() {
			return person -> String.format("HELLO, %s %s!",
					person.firstName().toUpperCase(Locale.ROOT),
					person.lastName().toUpperCase(Locale.ROOT));
		}
	}
}
