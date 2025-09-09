/*
 * Copyright 2020-present the original author or authors.
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
package org.springframework.cloud.function.kotlin.aws

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.function.adapter.aws.FunctionInvoker
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * @author Oleg Zhurakousky
 */
//@SpringBootTest()
//@ContextConfiguration(classes = [RestApplication::class, AwsKotlinTestsTests.TestConfiguration::class])
open class AwsKotlinTestsTests {
	var dynamoDbEvent:String = "{\n" +
		"  \"Records\": [\n" +
		"    {\n" +
		"      \"eventID\": \"dc1e145db718184b1c809f989335b168\",\n" +
		"      \"eventName\": \"INSERT\",\n" +
		"      \"eventVersion\": \"1.1\",\n" +
		"      \"eventSource\": \"aws:dynamodb\",\n" +
		"      \"awsRegion\": \"eu-central-1\",\n" +
		"      \"dynamodb\": {\n" +
		"        \"ApproximateCreationDateTime\": 1.689335433E9,\n" +
		"        \"Keys\": {\n" +
		"          \"version\": {\n" +
		"            \"N\": \"1\"\n" +
		"          },\n" +
		"          \"urlPath\": {\n" +
		"            \"S\": \"image/6037/2023/07/14/1d058d91-c9db-4c6a-aadf-4ab749de95d1.jpg\"\n" +
		"          }\n" +
		"        },\n" +
		"        \"NewImage\": {\n" +
		"          \"createdAt\": {\n" +
		"            \"N\": \"1689335427\"\n" +
		"          },\n" +
		"          \"provider\": {\n" +
		"            \"S\": \"XXXXXX\"\n" +
		"          },\n" +
		"          \"urlPath\": {\n" +
		"            \"S\": \"image/6037/2023/07/14/1d058d91-c9db-4c6a-aadf-4ab749de95d1.jpg\"\n" +
		"          },\n" +
		"          \"version\": {\n" +
		"            \"N\": \"1\"\n" +
		"          },\n" +
		"          \"status\": {\n" +
		"            \"S\": \"SUCCESS\"\n" +
		"          }\n" +
		"        },\n" +
		"        \"SequenceNumber\": \"1049234200000000032682603273\",\n" +
		"        \"SizeBytes\": 7869,\n" +
		"        \"StreamViewType\": \"NEW_IMAGE\"\n" +
		"      },\n" +
		"      \"eventSourceARN\": \"arn:aws:dynamodb:eu-central-1:xxxxxxx:table/example-results/stream/2022-12-06T16:23:45.860\"\n" +
		"    }\n" +
		"  ]\n" +
		"}"

	@Test
	open fun testDynamoDb() {
		System.setProperty("MAIN_CLASS", KotlinAwsLambdasConfiguration::class.java.getName())
		System.setProperty("spring.cloud.function.definition", "handleDynamoDbEvent")
		var invoker = FunctionInvoker()

		var targetStream = ByteArrayInputStream(this.dynamoDbEvent.toByteArray())
		var output = ByteArrayOutputStream()
		invoker.handleRequest(targetStream, output, null)
	}
}
