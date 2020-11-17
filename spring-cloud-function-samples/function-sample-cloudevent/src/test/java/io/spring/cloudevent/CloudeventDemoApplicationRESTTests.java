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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.MimeType;
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
        System.out.println(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/cloudevents+json;charset=utf-8"));

        RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asPOJOMessage"));
        ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

        assertThat(response.getBody()).isEqualTo("releaseDate:24-03-2004; releaseName:Spring Framework; version:1.0");

        re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asPOJO"));
		response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo("releaseDate:24-03-2004; releaseName:Spring Framework; version:1.0");
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SOURCE))
			.isEqualTo(Collections.singletonList("https://interface21.com/"));
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.TYPE))
			.isEqualTo(Collections.singletonList("com.interface21"));
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.ID)).isNotNull();
    }

	@Test
    public void testAsStracturalFormatToString() throws Exception {
        SpringApplication.run(CloudeventDemoApplication.class);

        String payload = "{\n" +
                "    \"ce-specversion\" : \"1.0\",\n" +
                "    \"ce-type\" : \"org.springframework\",\n" +
                "    \"ce-source\" : \"https://spring.io/\",\n" +
                "    \"ce-id\" : \"A234-1234-1234\",\n" +
                "    \"ce-datacontenttype\" : \"application/json\",\n" +
                "    \"ce-data\" : {\n" +
                "        \"version\" : \"1.0\",\n" +
                "        \"releaseName\" : \"Spring Framework\",\n" +
                "        \"releaseDate\" : \"24-03-2004\"\n" +
                "    }\n" +
                "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/cloudevents+json;charset=utf-8"));

        RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asStringMessage"));
        ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

        assertThat(response.getBody()).isEqualTo("{\"version\":\"1.0\",\"releaseName\":\"Spring Framework\",\"releaseDate\":\"24-03-2004\"}");

        re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/asString"));
		response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo("{\"version\":\"1.0\",\"releaseName\":\"Spring Framework\",\"releaseDate\":\"24-03-2004\"}");
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SOURCE))
			.isEqualTo(Collections.singletonList("https://interface21.com/"));
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.TYPE))
			.isEqualTo(Collections.singletonList("com.interface21"));
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.ID)).isNotNull();
    }

	@Test
	public void testAsBinaryMapToMap() throws Exception {
		SpringApplication.run(new Class[] {CloudeventDemoApplication.class}, new String[] {});

		HttpHeaders headers = this.buildHeaders(MediaType.APPLICATION_JSON);
		String payload = "{\"releaseDate\":\"24-03-2004\", \"releaseName\":\"Spring Framework\", \"version\":\"1.0\"}";

		RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/consumeAndProduceCloudEventAsMapToMap"));
		ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo("{\"releaseDate\":\"01-10-2050\",\"releaseName\":\"Spring Framework\",\"version\":\"10.0\"}");
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SOURCE))
			.isEqualTo(Collections.singletonList("https://interface21.com/"));
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.TYPE))
			.isEqualTo(Collections.singletonList("com.interface21"));
	}

	@Test
	public void testAsBinaryPojoToPojo() throws Exception {
		SpringApplication.run(new Class[] {CloudeventDemoApplication.class}, new String[] {});

		HttpHeaders headers = this.buildHeaders(MediaType.APPLICATION_JSON);
		String payload = "{\"releaseDate\":\"01-10-2006\", \"releaseName\":\"Spring Framework\", \"version\":\"1.0\"}";

		RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/consumeAndProduceCloudEventAsPojoToPojo"));
		ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo("{\"releaseDate\":\"01-10-2006\",\"releaseName\":\"Spring Framework\",\"version\":\"2.0\"}");
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SOURCE))
			.isEqualTo(Collections.singletonList("https://interface21.com/"));
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.TYPE))
			.isEqualTo(Collections.singletonList("com.interface21"));
	}


	/*
	 * Typically this would never happen since spec mandates that HTTP uses 'ce-` prefix.
	 * So this is to primarily validate that we can recognize it process it and still produce correct headers
	 */
	@Test
	public void testAsBinaryPojoToPojoWrongHeaders() throws Exception {
		SpringApplication.run(new Class[] {CloudeventDemoApplication.class}, new String[] {});

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + CloudEventMessageUtils.ID, UUID.randomUUID().toString());
		headers.set(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + CloudEventMessageUtils.SOURCE, "https://spring.io/");
		headers.set(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + CloudEventMessageUtils.SPECVERSION, "1.0");
		headers.set(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + CloudEventMessageUtils.TYPE, "org.springframework");
		String payload = "{\"releaseDate\":\"01-10-2006\", \"releaseName\":\"Spring Framework\", \"version\":\"1.0\"}";

		RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/consumeAndProduceCloudEventAsPojoToPojo"));
		ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

		assertThat(response.getBody()).isEqualTo("{\"releaseDate\":\"01-10-2006\",\"releaseName\":\"Spring Framework\",\"version\":\"2.0\"}");
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SOURCE))
			.isEqualTo(Collections.singletonList("https://interface21.com/"));
		assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.TYPE))
			.isEqualTo(Collections.singletonList("com.interface21"));
		assertThat(response.getHeaders().get(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + CloudEventMessageUtils.TYPE)).isNull();
		assertThat(response.getHeaders().get(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + CloudEventMessageUtils.SOURCE)).isNull();
		assertThat(response.getHeaders().get(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + CloudEventMessageUtils.ID)).isNull();
		assertThat(response.getHeaders().get(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + CloudEventMessageUtils.SPECVERSION)).isNull();
	}


	@Test
    public void testAsStructuralPojoToPojoDefaultDataContentType() throws Exception {
        ApplicationContext context = SpringApplication.run(CloudeventDemoApplication.class);
        JsonMapper mapper = context.getBean(JsonMapper.class);

        String payload = "{\n" +
                "    \"specversion\" : \"1.0\",\n" +
                "    \"type\" : \"org.springframework\",\n" +
                "    \"source\" : \"https://spring.io/\",\n" +
                "    \"id\" : \"A234-1234-1234\",\n" +
                "    \"data\" : {\n" +
                "        \"version\" : \"1.0\",\n" +
                "        \"releaseName\" : \"Spring Framework\",\n" +
                "        \"releaseDate\" : \"24-03-2004\"\n" +
                "    }\n" +
                "}";


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/cloudevents+json;charset=utf-8"));

        RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/consumeAndProduceCloudEventAsPojoToPojo"));
        ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);

        SpringReleaseEvent springReleaseEvent = mapper.fromJson(response.getBody(), SpringReleaseEvent.class);

        assertThat(springReleaseEvent.getReleaseName()).isEqualTo("Spring Framework");
        assertThat(springReleaseEvent.getVersion()).isEqualTo("2.0");

        re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/consumeAndProduceCloudEventAsMapToMap"));
        response = testRestTemplate.exchange(re, String.class);

        springReleaseEvent = mapper.fromJson(response.getBody(), SpringReleaseEvent.class);

        assertThat(springReleaseEvent.getReleaseName()).isEqualTo("Spring Framework");
        assertThat(springReleaseEvent.getVersion()).isEqualTo("10.0");

        assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SOURCE))
			.isEqualTo(Collections.singletonList("https://interface21.com/"));
        assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.TYPE))
			.isEqualTo(Collections.singletonList("com.interface21"));
        assertThat(response.getHeaders().get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.ID)).isNotNull();
    }

	@Test
	public void testPojoConsumer() throws Exception {
		ApplicationContext context = SpringApplication.run(new Class[] {CloudeventDemoApplication.class}, new String[] {});

		HttpHeaders headers = this.buildHeaders(MediaType.APPLICATION_JSON);
		String payload = "{\"releaseDate\":\"01-10-2006\", \"releaseName\":\"Spring Framework\", \"version\":\"1.0\"}";

		RequestEntity<String> re = new RequestEntity<>(payload, headers, HttpMethod.POST, this.constructURI("/pojoConsumer"));
		ResponseEntity<String> response = testRestTemplate.exchange(re, String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		CloudeventDemoApplication application = context.getBean(CloudeventDemoApplication.class);
		assertThat(application.consumerSuccess).isTrue();
	}

	private URI constructURI(String path) throws Exception {
		return new URI("http://localhost:" + System.getProperty("server.port") + path);
	}

	private HttpHeaders buildHeaders(MediaType contentType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		headers.set(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.ID, UUID.randomUUID().toString());
		headers.set(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SOURCE, "https://spring.io/");
		headers.set(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SPECVERSION, "1.0");
		headers.set(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.TYPE, "org.springframework");
		return headers;
	}

	@Configuration
	public static class FooBarConverterConfiguration {
		@Bean
		public MessageConverter foobar(JsonMapper jsonMapper) {
			return new FooBarToCloudEventMessageConverter(jsonMapper);
		}
	}

	public static class FooBarToCloudEventMessageConverter extends AbstractMessageConverter {

		public FooBarToCloudEventMessageConverter(JsonMapper jsonMapper) {
			super(new MimeType("foo", "bar"));
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

}
