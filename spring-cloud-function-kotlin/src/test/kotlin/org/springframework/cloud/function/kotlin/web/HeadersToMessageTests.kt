/*
 * Copyright 2020-2021 the original author or authors.
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
package org.springframework.cloud.function.kotlin.web

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.function.web.RestApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.context.ContextConfiguration
import java.lang.Exception
import java.net.URI

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
@SpringBootTest(
	webEnvironment = WebEnvironment.RANDOM_PORT,
	properties = ["spring.cloud.function.web.path=/functions", "spring.main.web-application-type=reactive"]
)
@ContextConfiguration(classes = [RestApplication::class, HeadersToMessageTests.TestConfiguration::class])
class HeadersToMessageTests {
	@Autowired
	private val rest: TestRestTemplate? = null
	@Test
	@Throws(Exception::class)
	fun testBodyAndCustomHeaderFromMessagePropagation() {
		// test POJO paylod
		var postForEntity = rest!!
			.exchange(
				RequestEntity.post(URI("/functions/employee"))
					.contentType(MediaType.APPLICATION_JSON)
					.body("{\"name\":\"Bob\",\"age\":25}"), String::class.java
			)
		Assertions.assertThat(postForEntity.body).isEqualTo("{\"name\":\"Bob\",\"age\":25}")
		Assertions.assertThat(postForEntity.headers.containsKey("x-content-type")).isTrue
		Assertions.assertThat(postForEntity.headers["x-content-type"]!![0])
			.isEqualTo("application/xml")
		Assertions.assertThat(postForEntity.headers["foo"]!![0]).isEqualTo("bar")

		// test simple type payload
		postForEntity = rest.postForEntity(
			URI("/functions/string"),
			"{\"name\":\"Bob\",\"age\":25}", String::class.java
		)
		Assertions.assertThat(postForEntity.body).isEqualTo("{\"name\":\"Bob\",\"age\":25}")
		Assertions.assertThat(postForEntity.headers.containsKey("x-content-type")).isTrue
		Assertions.assertThat(postForEntity.headers["x-content-type"]!![0])
			.isEqualTo("application/xml")
		Assertions.assertThat(postForEntity.headers["foo"]!![0]).isEqualTo("bar")
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	class TestConfiguration {
		@Bean("string")
		fun functiono(): (message: Message<String?>) -> Message<String> = { request: Message<String?> ->
			val message =
				MessageBuilder.withPayload(request.payload)
					.setHeader("X-Content-Type", "application/xml")
					.setHeader("foo", "bar").build()
			message
		}

		@Bean("employee")
		fun function1(): (employee: Message<Employee>) -> Message<Employee> = { request ->
			val message =
				MessageBuilder
					.withPayload(request.payload)
					.setHeader("X-Content-Type", "application/xml")
					.setHeader("foo", "bar").build()
			message
		}
	}

	// used by json converter
	class Employee {
		var name: String? = null
		var age = 0
	}
}
