/*
 * Copyright 2019-2019 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.function.adapter.test.aws.AWSCustomRuntime;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;


/**
 * @author Oleg Zhurakousky
 *
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {"spring.main.web-application-type=servlet"})
@ContextConfiguration(classes = {AWSCustomRuntime.class}, initializers = LambdaApplication.class)
@TestPropertySource(properties = {"_HANDLER=uppercase"})
public class LambdaApplicationTests {
	@Autowired
	private AWSCustomRuntime aws;

	@Test
	void testWithCustomRuntime() throws Exception {
		assertThat(aws.exchange("\"oleg\"").getPayload()).isEqualTo("\"OLEG\"");
		assertThat(aws.exchange("\"dave\"").getPayload()).isEqualTo("\"DAVE\"");
	}
}
