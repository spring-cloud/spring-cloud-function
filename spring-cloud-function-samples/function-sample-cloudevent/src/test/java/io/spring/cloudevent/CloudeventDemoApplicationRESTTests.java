/*
 * Copyright 2020-2020 the original author or authors.
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

package io.spring.cloudevent;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.context.config.CloudEventJsonMessageConverter;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.SocketUtils;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class CloudeventDemoApplicationRESTTests {

	private TestRestTemplate testRestTemplate = new TestRestTemplate();

	@BeforeEach
	public void init() throws Exception {
		System.setProperty("server.port", String.valueOf(SocketUtils.findAvailableTcpPort()));
	}

	/*
	 * This test demonstrates consumption of Cloud Event via HTTP POST - binary-mode message.
	 * According to specification - https://github.com/cloudevents/spec/blob/v1.0/spec.md
	 * 	- A "binary-mode message" is one where the event data is stored in the message body,
	 *    and event attributes are stored as part of message meta-data.
	 *
	 * The above means that it fits perfectly with Spring Message model and as such there is
	 * absolutely nothing that needs to be done at the framework or user level to consume it.
	 * It just works!
	 *
	 * The example demonstrated via two types of functions
	 *  - Function<Message<String>, String> asBinaryViaMessage;
	 *  - Function<String, String> asJustBinary;
	 */
	@Test
	public void testAsBinaryMessageViaHTTP() throws Exception {
		SpringApplication.run(CloudeventDemoApplication.class);
		HttpHeaders headers = this.buildHeaders(MediaType.APPLICATION_JSON);
		// will work with either content type
//		HttpHeaders headers = this.buildHeaders(MediaType.valueOf("application/cloudevents+json;charset=utf-8"));

		String payload = "{\"releaseDate\":\"2004-03-24\", \"releaseName\":\"Spring Framework\", \"version\":\"1.0\"}";

		RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asStringMessage"));
		ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo(payload);

		re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asString"));
		response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo(payload);
	}

	/*
	 * The same as the previous two tests with the exception that cloud event data de-serialized into POJO.
	 * Again, given that abstractions for transparent type conversion already part of the Spring ecosystem nothing
	 * needed to be done at the framework or user level to consume it.
	 * It just works!
	 */
	@Test
	public void testAsBinaryPOJOMessageViaHTTP() throws Exception {
		SpringApplication.run(CloudeventDemoApplication.class);

		HttpHeaders headers = this.buildHeaders(MediaType.APPLICATION_JSON);
		String payload = "{\"releaseDate\":\"24-03-2004\", \"releaseName\":\"Spring Framework\", \"version\":\"1.0\"}";

		RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asPOJOMessage"));
		ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo("releaseDate:24-03-2004; releaseName:Spring Framework; version:1.0");

		re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asPOJO"));
		response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo("releaseDate:24-03-2004; releaseName:Spring Framework; version:1.0");
	}

	/*
	 * This test demonstrates parsing of cloud event out of provided 'datacontenttype'
	 * using custom message converter which supports imaginary "contentType=foo/bar".
	 *
	 */
	@Test
	public void testAsBinaryPOJOMessageViaHTTPCustomDataType() throws Exception {
		SpringApplication.run(new Class[] {CloudeventDemoApplication.class, FooBarConverterConfiguration.class}, new String[] {});

		HttpHeaders headers = this.buildHeaders(MediaType.valueOf("application/cloudevents+json;charset=utf-8"));
		headers.set("datacontenttype", "foo/bar");
		String payload = "24-03-2004:Spring Framework:1.0";

		RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asPOJOMessage"));
		ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo("releaseDate:24-03-2004; releaseName:Spring Framework; version:1.0");
	}

	/*
	 * This test demonstrates sending structured
	 */
	@Test
    public void testAsStracturalFormatToPOJO() throws Exception {
        SpringApplication.run(CloudeventDemoApplication.class);

        String payload = "{\n" +
                "    \"specversion\" : \"1.0\",\n" +
                "    \"type\" : \"org.springframework\",\n" +
                "    \"source\" : \"https://spring.io/\",\n" +
                "    \"id\" : \"A234-1234-1234\",\n" +
                "    \"datacontenttype\" : \"application/json\",\n" +
                "    \"data\" : {\n" +
                "        \"version\" : \"1.0\",\n" +
                "        \"releaseName\" : \"Spring Framework\",\n" +
                "        \"releaseDate\" : \"24-03-2004\"\n" +
                "    }\n" +
                "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/cloudevents+json;charset=utf-8"));

        RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asPOJOMessage"));
        ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

        assertThat(response.getBody()).isEqualTo("releaseDate:24-03-2004; releaseName:Spring Framework; version:1.0");

        re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asPOJO"));
		response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo("releaseDate:24-03-2004; releaseName:Spring Framework; version:1.0");
    }

	@Test
    public void testAsStracturalFormatToString() throws Exception {
        SpringApplication.run(CloudeventDemoApplication.class);

        String payload = "{\n" +
                "    \"specversion\" : \"1.0\",\n" +
                "    \"type\" : \"org.springframework\",\n" +
                "    \"source\" : \"https://spring.io/\",\n" +
                "    \"id\" : \"A234-1234-1234\",\n" +
                "    \"datacontenttype\" : \"application/json\",\n" +
                "    \"data\" : {\n" +
                "        \"version\" : \"1.0\",\n" +
                "        \"releaseName\" : \"Spring Framework\",\n" +
                "        \"releaseDate\" : \"24-03-2004\"\n" +
                "    }\n" +
                "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/cloudevents+json;charset=utf-8"));

        RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asStringMessage"));
        ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

        assertThat(response.getBody()).isEqualTo(payload);

        re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asString"));
		response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo(payload);
    }


	@Configuration
	public static class FooBarConverterConfiguration {
		@Bean
		public MessageConverter foobar(JsonMapper jsonMapper) {
			return new FooBarToCloudEventMessageConverter(jsonMapper);
		}
	}

	public static class FooBarToCloudEventMessageConverter extends CloudEventJsonMessageConverter {

		public FooBarToCloudEventMessageConverter(JsonMapper jsonMapper) {
			super(jsonMapper);
		}

		@Override
		protected boolean supports(Class<?> clazz) {
			throw new UnsupportedOperationException();
		}

		@Override
		protected boolean canConvertTo(Object payload, @Nullable MessageHeaders headers) {
			if (!supportsMimeType(headers)) {
				return false;
			}
			return true;
		}
		@Override
		protected boolean canConvertFrom(Message<?> message, @Nullable Class<?> targetClass) {
			if (targetClass == null || !supportsMimeType(message.getHeaders())) {
				return false;
			}
			else if (message.getHeaders().containsKey("datacontenttype") && message.getHeaders().get("datacontenttype").equals("foo/bar")) {
				return true;
			}
			return false;
		}

		@Override
		protected Object convertFromInternal(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
			if (message.getHeaders().containsKey("datacontenttype")
					&& message.getHeaders().get("datacontenttype").equals("foo/bar")
					&& SpringReleaseEvent.class == targetClass) {
				SpringReleaseEvent event = new SpringReleaseEvent();
				String[] data = ((String) message.getPayload()).split(":");
				SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");
				try {
					event.setReleaseDate(df.parse(data[0].trim()));
				}
				catch (Exception e) {
					throw new IllegalArgumentException("Failed to convert date", e);
				}
				event.setReleaseName(data[1]);
				event.setVersion(data[2]);
				return event;
			}
			else {
				return super.convertFromInternal(message, targetClass, conversionHint);
			}
		}

		@Override
		protected Object convertToInternal(Object payload, @Nullable MessageHeaders headers,
				@Nullable Object conversionHint) {

			return null;

		}
	}

	private URI constructURI(String path) throws Exception {
		return new URI("http://localhost:" + System.getProperty("server.port") + path);
	}

	private HttpHeaders buildHeaders(MediaType contentType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		headers.set("id", UUID.randomUUID().toString());
		headers.set("source", "https://spring.io/");
		headers.set("specversion", "1.0");
		headers.set("type", "org.springframework");
		return headers;
	}

}
