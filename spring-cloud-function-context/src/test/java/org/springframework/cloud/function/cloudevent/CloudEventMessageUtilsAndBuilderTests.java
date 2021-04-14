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

package org.springframework.cloud.function.cloudevent;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 */
public class CloudEventMessageUtilsAndBuilderTests {

	@Test// see https://github.com/spring-cloud/spring-cloud-function/issues/680
	public void testProperAttributeExtractionRegardlessOfTargetProtocol() {
		Message<String> ceMessage = CloudEventMessageBuilder.withData("foo").build();
		ceMessage = MessageBuilder.fromMessage(ceMessage).setHeader("target-protocol", "kafka").build();

		String prefix = CloudEventMessageUtils.determinePrefixToUse(ceMessage.getHeaders());
		assertThat(prefix).isEqualTo("ce-");
		prefix = CloudEventMessageUtils.determinePrefixToUse(ceMessage.getHeaders(), true);
		assertThat(prefix).isEqualTo("ce_");

		String specVersion = CloudEventMessageUtils.getSpecVersion(ceMessage);
		assertThat(specVersion).isEqualTo("1.0");
		String type = CloudEventMessageUtils.getType(ceMessage);
		assertThat(type).isEqualTo("java.lang.String");
		String id = CloudEventMessageUtils.getId(ceMessage);
		assertThat(id).isNotNull();
		URI source = CloudEventMessageUtils.getSource(ceMessage);
		assertThat(source.toString()).isEqualTo("https://spring.io/");
	}

	@Test
	public void testAttributeRecognitionAndCanonicalization() {
		Message<String> httpMessage = MessageBuilder.withPayload("hello")
				.setHeader(CloudEventMessageUtils.SOURCE, "https://foo.bar")
				.setHeader(CloudEventMessageUtils.SPECVERSION, "1.0")
				.setHeader(CloudEventMessageUtils.TYPE, "blah")
				.setHeader("x", "x")
				.setHeader("zzz", "zzz")
				.build();
		Map<String, Object> attributes = CloudEventMessageUtils.getAttributes(httpMessage);
		assertThat(attributes.size()).isEqualTo(3);
		assertThat((String) CloudEventMessageUtils.getData(httpMessage)).isEqualTo("hello");

		Message<String> kafkaMessage = CloudEventMessageBuilder.fromMessage(httpMessage).build(CloudEventMessageUtils.KAFKA_ATTR_PREFIX);
		attributes = CloudEventMessageUtils.getAttributes(kafkaMessage);
		assertThat(attributes.size()).isEqualTo(4); // id will be auto injected, so always at least 4 (as tehre are 4 required attributes in CE)
		assertThat(kafkaMessage.getHeaders().get("ce_source")).isNotNull();
		assertThat(CloudEventMessageUtils.getSource(kafkaMessage)).isEqualTo(URI.create("https://foo.bar"));
		assertThat(kafkaMessage.getHeaders().get("ce_type")).isNotNull();
		assertThat(CloudEventMessageUtils.getType(kafkaMessage)).isEqualTo("blah");
		assertThat(kafkaMessage.getHeaders().get("ce_specversion")).isNotNull();
		assertThat(CloudEventMessageUtils.getSpecVersion(kafkaMessage)).isEqualTo("1.0");

		httpMessage = CloudEventMessageBuilder.fromMessage(kafkaMessage).build(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX);
		attributes = CloudEventMessageUtils.getAttributes(httpMessage);
		assertThat(attributes.size()).isEqualTo(4); //
		assertThat(httpMessage.getHeaders().get("ce-source")).isNotNull();
		assertThat(CloudEventMessageUtils.getSource(httpMessage)).isEqualTo(URI.create("https://foo.bar"));
		assertThat(httpMessage.getHeaders().get("ce-type")).isNotNull();
		assertThat(CloudEventMessageUtils.getType(httpMessage)).isEqualTo("blah");
		assertThat(httpMessage.getHeaders().get("ce-specversion")).isNotNull();
		assertThat(CloudEventMessageUtils.getSpecVersion(httpMessage)).isEqualTo("1.0");
	}
}
