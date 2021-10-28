/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class FunctionInvokerTests {

	ObjectMapper mapper = new ObjectMapper();

	String jsonCollection = "[\"Ricky\",\"Julien\",\"Bubbles\"]";

	String sampleLBEvent = "{" +
			"    \"requestContext\": {" +
			"        \"elb\": {" +
			"            \"targetGroupArn\": \"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/my-target-group/6d0ecf831eec9f09\"" +
			"        }" +
			"    }," +
			"    \"httpMethod\": \"GET\"," +
			"    \"path\": \"/\"," +
			"    \"headers\": {" +
			"        \"accept\": \"text/html,application/xhtml+xml\"," +
			"        \"accept-language\": \"en-US,en;q=0.8\"," +
			"        \"content-type\": \"text/plain\"," +
			"        \"cookie\": \"cookies\"," +
			"        \"host\": \"lambda-846800462-us-east-2.elb.amazonaws.com\"," +
			"        \"user-agent\": \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6)\"," +
			"        \"x-amzn-trace-id\": \"Root=1-5bdb40ca-556d8b0c50dc66f0511bf520\"," +
			"        \"x-forwarded-for\": \"72.21.198.66\"," +
			"        \"x-forwarded-port\": \"443\"," +
			"        \"x-forwarded-proto\": \"https\"" +
			"    }," +
			"    \"isBase64Encoded\": false," +
			"    \"body\": \"request_body\"" +
			"}";

	String sampleSQSEvent = "{\n" +
			"  \"Records\": [\n" +
			"    {\n" +
			"      \"messageId\": \"19dd0b57-b21e-4ac1-bd88-01bbb068cb78\",\n" +
			"      \"receiptHandle\": \"MessageReceiptHandle\",\n" +
			"      \"body\": \"Hello from SQS!\",\n" +
			"      \"attributes\": {\n" +
			"        \"ApproximateReceiveCount\": \"1\",\n" +
			"        \"SentTimestamp\": \"1523232000000\",\n" +
			"        \"SenderId\": \"123456789012\",\n" +
			"        \"ApproximateFirstReceiveTimestamp\": \"1523232000001\"\n" +
			"      },\n" +
			"      \"messageAttributes\": {},\n" +
			"      \"md5OfBody\": \"7b270e59b47ff90a553787216d55d91d\",\n" +
			"      \"eventSource\": \"aws:sqs\",\n" +
			"      \"eventSourceARN\": \"arn:aws:sqs:eu-central-1:123456789012:MyQueue\",\n" +
			"      \"awsRegion\": \"eu-central-1\"\n" +
			"    }\n" +
			"  ]\n" +
			"}";

	String sampleSNSEvent = "{\n" +
			"  \"Records\": [\n" +
			"    {\n" +
			"      \"EventVersion\": \"1.0\",\n" +
			"      \"EventSubscriptionArn\": \"arn:aws:sns:us-east-2:123456789012:sns-lambda:21be56ed-a058-49f5-8c98-aedd2564c486\",\n" +
			"      \"EventSource\": \"aws:sns\",\n" +
			"      \"Sns\": {\n" +
			"        \"SignatureVersion\": \"1\",\n" +
			"        \"Timestamp\": \"2019-01-02T12:45:07.000Z\",\n" +
			"        \"Signature\": \"tcc6faL2yUC6dgZdmrwh1Y4cGa/ebXEkAi6RibDsvpi+tE/1+82j...65r==\",\n" +
			"        \"SigningCertUrl\": \"https://sns.us-east-2.amazonaws.com/SimpleNotificationService-ac565b8b1a6c5d002d285f9598aa1d9b.pem\",\n" +
			"        \"MessageId\": \"95df01b4-ee98-5cb9-9903-4c221d41eb5e\",\n" +
			"        \"Message\": \"Hello from SNS!\",\n" +
			"        \"MessageAttributes\": {\n" +
			"          \"Test\": {\n" +
			"            \"Type\": \"String\",\n" +
			"            \"Value\": \"TestString\"\n" +
			"          },\n" +
			"          \"TestBinary\": {\n" +
			"            \"Type\": \"Binary\",\n" +
			"            \"Value\": \"TestBinary\"\n" +
			"          }\n" +
			"        },\n" +
			"        \"Type\": \"Notification\",\n" +
			"        \"UnsubscribeUrl\": \"https://sns.us-east-2.amazonaws.com/?Action=Unsubscribe&amp;SubscriptionArn=arn:aws:sns:us-east-2:123456789012:test-lambda:21be56ed-a058-49f5-8c98-aedd2564c486\",\n" +
			"        \"TopicArn\":\"arn:aws:sns:us-east-2:123456789012:sns-lambda\",\n" +
			"        \"Subject\": \"TestInvoke\"\n" +
			"      }\n" +
			"    }\n" +
			"  ]\n" +
			"}";

	String sampleKinesisEvent = "{" +
			"    \"Records\": [" +
			"        {" +
			"            \"kinesis\": {" +
			"                \"kinesisSchemaVersion\": \"1.0\"," +
			"                \"partitionKey\": \"1\"," +
			"                \"sequenceNumber\": \"49590338271490256608559692538361571095921575989136588898\"," +
			"                \"data\": \"SGVsbG8sIHRoaXMgaXMgYSB0ZXN0Lg==\"," +
			"                \"approximateArrivalTimestamp\": 1545084650.987" +
			"            }," +
			"            \"eventSource\": \"aws:kinesis\"," +
			"            \"eventVersion\": \"1.0\"," +
			"            \"eventID\": \"shardId-000000000006:49590338271490256608559692538361571095921575989136588898\"," +
			"            \"eventName\": \"aws:kinesis:record\"," +
			"            \"invokeIdentityArn\": \"arn:aws:iam::123456789012:role/lambda-role\"," +
			"            \"awsRegion\": \"us-east-2\"," +
			"            \"eventSourceARN\": \"arn:aws:kinesis:us-east-2:123456789012:stream/lambda-stream\"" +
			"        }," +
			"        {" +
			"            \"kinesis\": {" +
			"                \"kinesisSchemaVersion\": \"1.0\"," +
			"                \"partitionKey\": \"1\"," +
			"                \"sequenceNumber\": \"49590338271490256608559692540925702759324208523137515618\"," +
			"                \"data\": \"VGhpcyBpcyBvbmx5IGEgdGVzdC4=\"," +
			"                \"approximateArrivalTimestamp\": 1545084711.166" +
			"            }," +
			"            \"eventSource\": \"aws:kinesis\"," +
			"            \"eventVersion\": \"1.0\"," +
			"            \"eventID\": \"shardId-000000000006:49590338271490256608559692540925702759324208523137515618\"," +
			"            \"eventName\": \"aws:kinesis:record\"," +
			"            \"invokeIdentityArn\": \"arn:aws:iam::123456789012:role/lambda-role\"," +
			"            \"awsRegion\": \"us-east-2\"," +
			"            \"eventSourceARN\": \"arn:aws:kinesis:us-east-2:123456789012:stream/lambda-stream\"" +
			"        }" +
			"    ]" +
			"}";

	//https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-lambda.html
	String apiGatewayV2Event = "{\n" +
			"  \"version\": \"2.0\",\n" +
			"  \"routeKey\": \"$default\",\n" +
			"  \"rawPath\": \"/my/path\",\n" +
			"  \"rawQueryString\": \"parameter1=value1&parameter1=value2&parameter2=value\",\n" +
			"  \"cookies\": [\n" +
			"    \"cookie1\",\n" +
			"    \"cookie2\"\n" +
			"  ],\n" +
			"  \"headers\": {\n" +
			"    \"header1\": \"value1\",\n" +
			"    \"header2\": \"value1,value2\"\n" +
			"  },\n" +
			"  \"queryStringParameters\": {\n" +
			"    \"parameter1\": \"value1,value2\",\n" +
			"    \"parameter2\": \"value\"\n" +
			"  },\n" +
			"  \"requestContext\": {\n" +
			"    \"accountId\": \"123456789012\",\n" +
			"    \"apiId\": \"api-id\",\n" +
			"    \"authentication\": {\n" +
			"      \"clientCert\": {\n" +
			"        \"clientCertPem\": \"CERT_CONTENT\",\n" +
			"        \"subjectDN\": \"www.example.com\",\n" +
			"        \"issuerDN\": \"Example issuer\",\n" +
			"        \"serialNumber\": \"a1:a1:a1:a1:a1:a1:a1:a1:a1:a1:a1:a1:a1:a1:a1:a1\",\n" +
			"        \"validity\": {\n" +
			"          \"notBefore\": \"May 28 12:30:02 2019 GMT\",\n" +
			"          \"notAfter\": \"Aug  5 09:36:04 2021 GMT\"\n" +
			"        }\n" +
			"      }\n" +
			"    },\n" +
			"    \"authorizer\": {\n" +
			"      \"jwt\": {\n" +
			"        \"claims\": {\n" +
			"          \"claim1\": \"value1\",\n" +
			"          \"claim2\": \"value2\"\n" +
			"        },\n" +
			"        \"scopes\": [\n" +
			"          \"scope1\",\n" +
			"          \"scope2\"\n" +
			"        ]\n" +
			"      }\n" +
			"    },\n" +
			"    \"domainName\": \"id.execute-api.us-east-1.amazonaws.com\",\n" +
			"    \"domainPrefix\": \"id\",\n" +
			"    \"http\": {\n" +
			"      \"method\": \"POST\",\n" +
			"      \"path\": \"/my/path\",\n" +
			"      \"protocol\": \"HTTP/1.1\",\n" +
			"      \"sourceIp\": \"IP\",\n" +
			"      \"userAgent\": \"agent\"\n" +
			"    },\n" +
			"    \"requestId\": \"id\",\n" +
			"    \"routeKey\": \"$default\",\n" +
			"    \"stage\": \"$default\",\n" +
			"    \"time\": \"12/Mar/2020:19:03:58 +0000\",\n" +
			"    \"timeEpoch\": 1583348638390\n" +
			"  },\n" +
			"  \"body\": \"Hello from Lambda\",\n" +
			"  \"pathParameters\": {\n" +
			"    \"parameter1\": \"value1\"\n" +
			"  },\n" +
			"  \"isBase64Encoded\": false,\n" +
			"  \"stageVariables\": {\n" +
			"    \"stageVariable1\": \"value1\",\n" +
			"    \"stageVariable2\": \"value2\"\n" +
			"  }\n" +
			"}";

	String apiGatewayEvent = "{\n" +
			"    \"resource\": \"/uppercase2\",\n" +
			"    \"path\": \"/uppercase2\",\n" +
			"    \"httpMethod\": \"POST\",\n" +
			"    \"headers\": {\n" +
			"        \"accept\": \"*/*\",\n" +
			"        \"content-type\": \"application/json\",\n" +
			"        \"Host\": \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\",\n" +
			"        \"User-Agent\": \"curl/7.54.0\",\n" +
			"        \"X-Amzn-Trace-Id\": \"Root=1-5ece339e-e0595766066d703ec70f1522\",\n" +
			"        \"X-Forwarded-For\": \"90.37.8.133\",\n" +
			"        \"X-Forwarded-Port\": \"443\",\n" +
			"        \"X-Forwarded-Proto\": \"https\"\n" +
			"    },\n" +
			"    \"multiValueHeaders\": {\n" +
			"        \"accept\": [\n" +
			"            \"*/*\"\n" +
			"        ],\n" +
			"        \"content-type\": [\n" +
			"            \"application/json\"\n" +
			"        ],\n" +
			"        \"Host\": [\n" +
			"            \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\"\n" +
			"        ],\n" +
			"        \"User-Agent\": [\n" +
			"            \"curl/7.54.0\"\n" +
			"        ],\n" +
			"        \"X-Amzn-Trace-Id\": [\n" +
			"            \"Root=1-5ece339e-e0595766066d703ec70f1522\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-For\": [\n" +
			"            \"90.37.8.133\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-Port\": [\n" +
			"            \"443\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-Proto\": [\n" +
			"            \"https\"\n" +
			"        ]\n" +
			"    },\n" +
			"    \"queryStringParameters\": null,\n" +
			"    \"multiValueQueryStringParameters\": null,\n" +
			"    \"pathParameters\": null,\n" +
			"    \"stageVariables\": null,\n" +
			"    \"requestContext\": {\n" +
			"        \"resourceId\": \"qf0io6\",\n" +
			"        \"resourcePath\": \"/uppercase2\",\n" +
			"        \"httpMethod\": \"POST\",\n" +
			"        \"extendedRequestId\": \"NL0A1EokCGYFZOA=\",\n" +
			"        \"requestTime\": \"27/May/2020:09:32:14 +0000\",\n" +
			"        \"path\": \"/test/uppercase2\",\n" +
			"        \"accountId\": \"313369169943\",\n" +
			"        \"protocol\": \"HTTP/1.1\",\n" +
			"        \"stage\": \"test\",\n" +
			"        \"domainPrefix\": \"fhul32ccy2\",\n" +
			"        \"requestTimeEpoch\": 1590571934872,\n" +
			"        \"requestId\": \"b96500aa-f92a-43c3-9360-868ba4053a00\",\n" +
			"        \"identity\": {\n" +
			"            \"cognitoIdentityPoolId\": null,\n" +
			"            \"accountId\": null,\n" +
			"            \"cognitoIdentityId\": null,\n" +
			"            \"caller\": null,\n" +
			"            \"sourceIp\": \"90.37.8.133\",\n" +
			"            \"principalOrgId\": null,\n" +
			"            \"accessKey\": null,\n" +
			"            \"cognitoAuthenticationType\": null,\n" +
			"            \"cognitoAuthenticationProvider\": null,\n" +
			"            \"userArn\": null,\n" +
			"            \"userAgent\": \"curl/7.54.0\",\n" +
			"            \"user\": null\n" +
			"        },\n" +
			"        \"domainName\": \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\",\n" +
			"        \"apiId\": \"fhul32ccy2\"\n" +
			"    },\n" +
			"    \"body\":\"hello\",\n" +
			"    \"isBase64Encoded\": false\n" +
			"}";

	String s3Event = "{\n" +
			"   \"Records\":[\n" +
			"      {\n" +
			"         \"eventVersion\":\"2.1\",\n" +
			"         \"eventSource\":\"aws:s3\",\n" +
			"         \"awsRegion\":\"us-east-2\",\n" +
			"         \"eventTime\":\"2020-07-15T21:29:41.365Z\",\n" +
			"         \"eventName\":\"ObjectCreated:Put\",\n" +
			"         \"userIdentity\":{\n" +
			"            \"principalId\":\"AWS:AIxxx\"\n" +
			"         },\n" +
			"         \"requestParameters\":{\n" +
			"            \"sourceIPAddress\":\"xxxx\"\n" +
			"         },\n" +
			"         \"responseElements\":{\n" +
			"            \"x-amz-request-id\":\"xxxx\",\n" +
			"            \"x-amz-id-2\":\"xxx/=\"\n" +
			"         },\n" +
			"         \"s3\":{\n" +
			"            \"s3SchemaVersion\":\"1.0\",\n" +
			"            \"configurationId\":\"New Data Delivery\",\n" +
			"            \"bucket\":{\n" +
			"               \"name\":\"bucket\",\n" +
			"               \"ownerIdentity\":{\n" +
			"                  \"principalId\":\"xxx\"\n" +
			"               },\n" +
			"               \"arn\":\"arn:aws:s3:::bucket\"\n" +
			"            },\n" +
			"            \"object\":{\n" +
			"               \"key\":\"test/file.geojson\",\n" +
			"               \"size\":32711,\n" +
			"               \"eTag\":\"aaaa\",\n" +
			"               \"sequencer\":\"aaaa\"\n" +
			"            }\n" +
			"         }\n" +
			"      }\n" +
			"   ]\n" +
			"}";

	String apiGatewayEventWithStructuredBody = "{\n" +
			"    \"resource\": \"/uppercase2\",\n" +
			"    \"path\": \"/uppercase2\",\n" +
			"    \"httpMethod\": \"POST\",\n" +
			"    \"headers\": {\n" +
			"        \"accept\": \"*/*\",\n" +
			"        \"content-type\": \"application/json\",\n" +
			"        \"Host\": \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\",\n" +
			"        \"User-Agent\": \"curl/7.54.0\",\n" +
			"        \"X-Amzn-Trace-Id\": \"Root=1-5ece339e-e0595766066d703ec70f1522\",\n" +
			"        \"X-Forwarded-For\": \"90.37.8.133\",\n" +
			"        \"X-Forwarded-Port\": \"443\",\n" +
			"        \"X-Forwarded-Proto\": \"https\"\n" +
			"    },\n" +
			"    \"multiValueHeaders\": {\n" +
			"        \"accept\": [\n" +
			"            \"*/*\"\n" +
			"        ],\n" +
			"        \"content-type\": [\n" +
			"            \"application/json\"\n" +
			"        ],\n" +
			"        \"Host\": [\n" +
			"            \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\"\n" +
			"        ],\n" +
			"        \"User-Agent\": [\n" +
			"            \"curl/7.54.0\"\n" +
			"        ],\n" +
			"        \"X-Amzn-Trace-Id\": [\n" +
			"            \"Root=1-5ece339e-e0595766066d703ec70f1522\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-For\": [\n" +
			"            \"90.37.8.133\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-Port\": [\n" +
			"            \"443\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-Proto\": [\n" +
			"            \"https\"\n" +
			"        ]\n" +
			"    },\n" +
			"    \"queryStringParameters\": null,\n" +
			"    \"multiValueQueryStringParameters\": null,\n" +
			"    \"pathParameters\": null,\n" +
			"    \"stageVariables\": null,\n" +
			"    \"requestContext\": {\n" +
			"        \"resourceId\": \"qf0io6\",\n" +
			"        \"resourcePath\": \"/uppercase2\",\n" +
			"        \"httpMethod\": \"POST\",\n" +
			"        \"extendedRequestId\": \"NL0A1EokCGYFZOA=\",\n" +
			"        \"requestTime\": \"27/May/2020:09:32:14 +0000\",\n" +
			"        \"path\": \"/test/uppercase2\",\n" +
			"        \"accountId\": \"313369169943\",\n" +
			"        \"protocol\": \"HTTP/1.1\",\n" +
			"        \"stage\": \"test\",\n" +
			"        \"domainPrefix\": \"fhul32ccy2\",\n" +
			"        \"requestTimeEpoch\": 1590571934872,\n" +
			"        \"requestId\": \"b96500aa-f92a-43c3-9360-868ba4053a00\",\n" +
			"        \"identity\": {\n" +
			"            \"cognitoIdentityPoolId\": null,\n" +
			"            \"accountId\": null,\n" +
			"            \"cognitoIdentityId\": null,\n" +
			"            \"caller\": null,\n" +
			"            \"sourceIp\": \"90.37.8.133\",\n" +
			"            \"principalOrgId\": null,\n" +
			"            \"accessKey\": null,\n" +
			"            \"cognitoAuthenticationType\": null,\n" +
			"            \"cognitoAuthenticationProvider\": null,\n" +
			"            \"userArn\": null,\n" +
			"            \"userAgent\": \"curl/7.54.0\",\n" +
			"            \"user\": null\n" +
			"        },\n" +
			"        \"domainName\": \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\",\n" +
			"        \"apiId\": \"fhul32ccy2\"\n" +
			"    },\n" +
			"    \"body\":{\"name\":\"Jim Lahey\"},\n" +
			"    \"isBase64Encoded\": false\n" +
			"}";

	@BeforeEach
	public void before() throws Exception {
		System.clearProperty("MAIN_CLASS");
		System.clearProperty("spring.cloud.function.routing-expression");
		System.clearProperty("spring.cloud.function.definition");
		this.getEnvironment().clear();
	}

	@Test
	public void testCollection() throws Exception {
		System.setProperty("MAIN_CLASS", SampleConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoStringReactive");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.jsonCollection.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).isEqualTo(this.jsonCollection);
	}

	@Test
	public void testKinesisStringEvent() throws Exception {
		System.setProperty("MAIN_CLASS", KinesisConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoString");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleKinesisEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("kinesisSchemaVersion");
	}

	@Test
	public void testKinesisEvent() throws Exception {
		System.setProperty("MAIN_CLASS", KinesisConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputKinesisEvent");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleKinesisEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("49590338271490256608559692538361571095921575989136588898");
	}

	@Test
	public void testKinesisEventAsMessage() throws Exception {
		System.setProperty("MAIN_CLASS", KinesisConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputKinesisEventAsMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleKinesisEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("49590338271490256608559692538361571095921575989136588898");
	}

	@Test
	public void testKinesisEventAsMap() throws Exception {
		System.setProperty("MAIN_CLASS", KinesisConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputKinesisEventAsMap");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleKinesisEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("49590338271490256608559692538361571095921575989136588898");
	}

	@Test
	public void testSQSStringEvent() throws Exception {
		System.setProperty("MAIN_CLASS", SQSConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoString");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleSQSEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result.length()).isEqualTo(14); // some additional JSON formatting
	}

	@Test
	public void testSQSEvent() throws Exception {
		System.setProperty("MAIN_CLASS", SQSConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputSQSEvent");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleSQSEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("arn:aws:sqs:eu-central-1:123456789012:MyQueue");
	}

	@Test
	public void testSQSEventAsMessage() throws Exception {
		System.setProperty("MAIN_CLASS", SQSConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputSQSEventAsMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleSQSEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("arn:aws:sqs:eu-central-1:123456789012:MyQueue");
	}

	@Test
	public void testSQSEventAsMap() throws Exception {
		System.setProperty("MAIN_CLASS", SQSConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputSQSEventAsMap");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleSQSEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("arn:aws:sqs:eu-central-1:123456789012:MyQueue");
	}

	@Test
	public void testSNSStringEvent() throws Exception {
		System.setProperty("MAIN_CLASS", SNSConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoString");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleSNSEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("arn:aws:sns");
	}

	@Test
	public void testSNSEvent() throws Exception {
		System.setProperty("MAIN_CLASS", SNSConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputSNSEvent");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleSNSEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("arn:aws:sns");
	}

	@Test
	public void testSNSEventAsMessage() throws Exception {
		System.setProperty("MAIN_CLASS", SNSConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputSNSEventAsMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleSNSEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("arn:aws:sns");
	}

	@Test
	public void testSNSEventAsMap() throws Exception {
		System.setProperty("MAIN_CLASS", SNSConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputSNSEventAsMap");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleSNSEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("arn:aws:sns");
	}

	@Test
	public void testS3StringEvent() throws Exception {
		System.setProperty("MAIN_CLASS", S3Configuration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoString");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.s3Event.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("s3SchemaVersion");
	}

	@Test
	public void testS3Event() throws Exception {

//		S3EventSerializer<S3Event> ser = new S3EventSerializer<S3Event>().withClass(S3Event.class).withClassLoader(S3Event.class.getClassLoader());
//		InputStream targetStream = new ByteArrayInputStream(this.s3Event.getBytes());
//		S3Event event = ser.fromJson(targetStream);
//		System.out.println(event);

		System.setProperty("MAIN_CLASS", S3Configuration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputS3Event");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.s3Event.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("s3SchemaVersion");
	}

	@Test
	public void testS3EventAsMessage() throws Exception {
		System.setProperty("MAIN_CLASS", S3Configuration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputS3EventAsMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.s3Event.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("s3SchemaVersion");
	}

	@Test
	public void testS3EventAsMap() throws Exception {
		System.setProperty("MAIN_CLASS", S3Configuration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputS3EventAsMap");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.s3Event.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("s3SchemaVersion");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayStringEventBody() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "uppercase");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("HELLO");

		System.clearProperty("spring.cloud.function.definition");
		System.setProperty("spring.cloud.function.routing-expression", "'uppercase'");
		invoker = new FunctionInvoker();
		targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		result = this.mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("HELLO");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayPojoEventBody() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "uppercasePojo");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEventWithStructuredBody.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("JIM LAHEY");

		System.clearProperty("spring.cloud.function.definition");
		System.setProperty("spring.cloud.function.routing-expression", "'uppercasePojo'");
		invoker = new FunctionInvoker();
		targetStream = new ByteArrayInputStream(this.apiGatewayEventWithStructuredBody.getBytes());
		output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		result = this.mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("JIM LAHEY");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayEvent() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputApiEvent");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("hello");

		System.clearProperty("spring.cloud.function.definition");
		System.setProperty("spring.cloud.function.routing-expression", "'inputApiEvent'");
		invoker = new FunctionInvoker();
		targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		result = this.mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("hello");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayV2Event() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputApiV2Event");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayV2Event.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("Hello from Lambda");

		System.clearProperty("spring.cloud.function.definition");
		System.setProperty("spring.cloud.function.routing-expression", "'inputApiV2Event'");
		invoker = new FunctionInvoker();
		targetStream = new ByteArrayInputStream(this.apiGatewayV2Event.getBytes());
		output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		result = this.mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("Hello from Lambda");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayAsSupplier() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "supply");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("boom");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayInAndOut() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputOutputApiEvent");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("hello");
		Map headers = (Map) result.get("headers");
		assertThat(headers.get("foo")).isEqualTo("bar");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayInAndOutV2() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputOutputApiEventV2");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("hello");
		Map headers = (Map) result.get("headers");
		assertThat(headers.get("foo")).isEqualTo("bar");
	}

//	@SuppressWarnings("rawtypes")
//	@Test
//	public void testApiGatewayInAndOutWithException() throws Exception {
//		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
//		System.setProperty("spring.cloud.function.definition", "inputOutputApiEventException");
//		FunctionInvoker invoker = new FunctionInvoker();
//
//		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
//		ByteArrayOutputStream output = new ByteArrayOutputStream();
//		invoker.handleRequest(targetStream, output, null);
//
//		Map result = mapper.readValue(output.toByteArray(), Map.class);
//		assertThat(result.get("body")).isEqualTo("Intentional");
//
//		Map headers = (Map) result.get("headers");
//		assertThat(headers.get("foo")).isEqualTo("bar");
//	}



	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayEventAsMessage() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputApiEventAsMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("hello");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayEventAsMap() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputApiEventAsMap");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("hello");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayEventConsumer() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "consume");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("\"OK\"");
	}

	@Test
	public void testWithDefaultRoutingFailure() throws Exception {
		System.setProperty("MAIN_CLASS", SampleConfiguration.class.getName());
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(((String) result.get("body"))).startsWith("Failed to establish route, since neither were provided:");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testWithDefaultRouting() throws Exception {
		System.setProperty("MAIN_CLASS", SampleConfiguration.class.getName());
		System.setProperty("spring.cloud.function.routing-expression", "'reverse'");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("olleh");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testWithDefinitionEnvVariable() throws Exception {

		System.setProperty("MAIN_CLASS", SampleConfiguration.class.getName());
		this.getEnvironment().put("SPRING_CLOUD_FUNCTION_DEFINITION", "reverse|uppercase");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("OLLEH");
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> getEnvironment() throws Exception {
		Map<String, String> env = System.getenv();
		Field field = env.getClass().getDeclaredField("m");
		field.setAccessible(true);
		return (Map<String, String>) field.get(env);
	}

	@EnableAutoConfiguration
	@Configuration
	public static class SampleConfiguration {
		@Bean
		public Function<String, String> echoString() {
			return v -> v;
		}

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		@Bean
		public Function<String, String> reverse() {
			return v -> new StringBuilder(v).reverse().toString();
		}

		@Bean
		public Function<Flux<String>, Flux<String>> echoStringReactive() {
			return v -> v;
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class KinesisConfiguration {
		@Bean
		public Function<String, String> echoString() {
			return v -> v;
		}

		@Bean
		public Function<KinesisEvent, String> inputKinesisEvent() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<Message<KinesisEvent>, String> inputKinesisEventAsMessage() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<Map<String, Object>, String> inputKinesisEventAsMap() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class SQSConfiguration {
		@Bean
		public Function<Person, String> echoString() {
			return v -> {
				System.out.println("Echo: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<SQSEvent, String> inputSQSEvent() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<Message<SQSEvent>, String> inputSQSEventAsMessage() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<Map<String, Object>, String> inputSQSEventAsMap() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public MyCustomMessageConverter messageConverter() {
			return new MyCustomMessageConverter();
		}
	}

	public static class MyCustomMessageConverter extends AbstractMessageConverter {

		public MyCustomMessageConverter() {
			super(new MimeType("*", "*"));
		}

		@Override
		protected boolean supports(Class<?> clazz) {
			return (Person.class.equals(clazz));
		}

		@Override
		protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
			Object payload = message.getPayload();
			String v = payload instanceof String ? (String) payload : new String((byte[]) payload);
			Person person = new Person();
			person.setName(v.substring(0, 10));
			return person;
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class SNSConfiguration {
		@Bean
		public Function<String, String> echoString() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<SNSEvent, String> inputSNSEvent() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<Message<SNSEvent>, String> inputSNSEventAsMessage() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<Map<String, Object>, String> inputSNSEventAsMap() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class S3Configuration {
		@Bean
		public Function<String, String> echoString() {
			return v -> v;
		}

		@Bean
		public Function<S3Event, String> inputS3Event(JsonMapper jsonMapper) {
			return v -> {
				System.out.println("Received: " + v);
				return jsonMapper.toString(v);
			};
		}

		@Bean
		public Function<Message<S3Event>, String> inputS3EventAsMessage(JsonMapper jsonMapper) {
			return v -> {
				System.out.println("Received: " + v);
				return jsonMapper.toString(v);
			};
		}

		@Bean
		public Function<Map<String, Object>, String> inputS3EventAsMap() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class ApiGatewayConfiguration {

		@Bean
		public Supplier<String> supply() {
			return () -> "boom";
		}


		@Bean
		public Consumer<String> consume() {
			return v -> System.out.println(v);
		}

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		@Bean
		public Function<Person, String> uppercasePojo() {
			return v -> {
				return v.getName().toUpperCase();
			};
		}

		@Bean
		public Function<APIGatewayProxyRequestEvent, String> inputApiEvent() {
			return v -> {
				return v.getBody();
			};
		}

		@Bean
		public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> inputOutputApiEvent() {
			return v -> {
				APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
				response.setBody(v.getBody());
				response.setStatusCode(200);
				response.setHeaders(Collections.singletonMap("foo", "bar"));
				return response;
			};
		}

		@Bean
		public Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> inputOutputApiEventV2() {
			return v -> {
				APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
				response.setBody(v.getBody());
				response.setStatusCode(200);
				response.setHeaders(Collections.singletonMap("foo", "bar"));
				return response;
			};
		}

		@Bean
		public Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> inputOutputApiEventException() {
			return v -> {
				throw new IllegalStateException("Intentional");
			};
		}

		@Bean
		public Function<APIGatewayV2HTTPEvent, String> inputApiV2Event() {
			return v -> {
				return v.getBody();
			};
		}

		@Bean
		public Function<Message<APIGatewayProxyRequestEvent>, String> inputApiEventAsMessage() {
			return v -> {
				return v.getPayload().getBody();
			};
		}

		@Bean
		public Function<Map<String, Object>, String> inputApiEventAsMap() {
			return v -> {
				String body = (String) v.get("body");
				return body;
			};
		}
	}

	public static class Person {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}
}
