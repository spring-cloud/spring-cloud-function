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

package org.springframework.cloud.function.adapter.aws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class FunctionInvokerTests {

	ObjectMapper mapper = new ObjectMapper();

	String jsonCollection = "[\"Ricky\",\"Julien\",\"Bubbles\"]";

	String jsonPojoCollection = "[{\"name\":\"Ricky\"},{\"name\":\"Julien\"},{\"name\":\"Julien\"}]";

	String someEvent = "{\n"
			+ "    \"payload\": {\n"
			+ "        \"headers\": {\n"
			+ "            \"businessUnit\": \"1\"\n"
			+ "        }\n"
			+ "    },\n"
			+ "    \"headers\": {\n"
			+ "        \"aws-context\": {\n"
			+ "            \"memoryLimit\": 1024,\n"
			+ "            \"awsRequestId\": \"87a211bf-540f-4f9f-a218-d096a0099999\",\n"
			+ "            \"functionName\": \"myfunction\",\n"
			+ "            \"functionVersion\": \"278\",\n"
			+ "            \"invokedFunctionArn\": \"arn:aws:lambda:us-east-1:xxxxxxx:function:xxxxx:snapstart\",\n"
			+ "            \"deadlineTimeInMs\": 1712717704761,\n"
			+ "            \"logger\": {\n"
			+ "                \"logFiltering\": {\n"
			+ "                    \"minimumLogLevel\": \"UNDEFINED\"\n"
			+ "                },\n"
			+ "                \"logFormatter\": {},\n"
			+ "                \"logFormat\": \"TEXT\"\n"
			+ "            }\n"
			+ "        },\n"
			+ "        \"businessUnit\": \"1\",\n"
			+ "        \"id\": \"xxxx\",\n"
			+ "        \"aws-event\": true,\n"
			+ "        \"timestamp\": 1712716805129\n"
			+ "    }\n"
			+ "}";

	String scheduleEvent = "{\n"
			+ "  \"version\": \"0\",\n"
			+ "  \"id\": \"17793124-05d4-b198-2fde-7ededc63b103\",\n"
			+ "  \"detail-type\": \"Object Created\",\n"
			+ "  \"source\": \"aws.s3\",\n"
			+ "  \"account\": \"111122223333\",\n"
			+ "  \"time\": \"2021-11-12T00:00:00Z\",\n"
			+ "  \"region\": \"ca-central-1\",\n"
			+ "  \"resources\": [\n"
			+ "    \"arn:aws:s3:::amzn-s3-demo-bucket1\"\n"
			+ "  ],\n"
			+ "  \"detail\": {\n"
			+ "    \"version\": \"0\",\n"
			+ "    \"bucket\": {\n"
			+ "      \"name\": \"amzn-s3-demo-bucket1\"\n"
			+ "    },\n"
			+ "    \"object\": {\n"
			+ "      \"key\": \"example-key\",\n"
			+ "      \"size\": 5,\n"
			+ "      \"etag\": \"b1946ac92492d2347c6235b4d2611184\",\n"
			+ "      \"version-id\": \"IYV3p45BT0ac8hjHg1houSdS1a.Mro8e\",\n"
			+ "      \"sequencer\": \"617f08299329d189\"\n"
			+ "    },\n"
			+ "    \"request-id\": \"N4N7GDK58NMKJ12R\",\n"
			+ "    \"requester\": \"123456789012\",\n"
			+ "    \"source-ip-address\": \"1.2.3.4\",\n"
			+ "    \"reason\": \"PutObject\"\n"
			+ "  }\n"
			+ "}  ";

	String dynamoDbEvent = "{\n"
			+ "  \"Records\": [\n"
			+ "    {\n"
			+ "      \"eventID\": \"f07f8ca4b0b26cb9c4e5e77e69f274ee\",\n"
			+ "      \"eventName\": \"INSERT\",\n"
			+ "      \"eventVersion\": \"1.1\",\n"
			+ "      \"eventSource\": \"aws:dynamodb\",\n"
			+ "      \"awsRegion\": \"us-east-1\",\n"
			+ "      \"userIdentity\":{\n"
			+ "        \"type\":\"Service\",\n"
			+ "        \"principalId\":\"dynamodb.amazonaws.com\"\n"
			+ "      },\n"
			+ "      \"dynamodb\": {\n"
			+ "        \"ApproximateCreationDateTime\": 1.684934517E9,\n"
			+ "        \"Keys\": {\n"
			+ "          \"val\": {\n"
			+ "            \"S\": \"data\"\n"
			+ "          },\n"
			+ "          \"key\": {\n"
			+ "            \"S\": \"binary\"\n"
			+ "          }\n"
			+ "        },\n"
			+ "        \"NewImage\": {\n"
			+ "          \"val\": {\n"
			+ "            \"S\": \"data\"\n"
			+ "          },\n"
			+ "          \"asdf1\": {\n"
			+ "            \"B\": \"AAEqQQ==\"\n"
			+ "          },\n"
			+ "          \"asdf2\": {\n"
			+ "            \"BS\": [\n"
			+ "              \"AAEqQQ==\",\n"
			+ "              \"QSoBAA==\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"key\": {\n"
			+ "            \"S\": \"binary\"\n"
			+ "          }\n"
			+ "        },\n"
			+ "        \"SequenceNumber\": \"1405400000000002063282832\",\n"
			+ "        \"SizeBytes\": 54,\n"
			+ "        \"StreamViewType\": \"NEW_AND_OLD_IMAGES\"\n"
			+ "      },\n"
			+ "      \"eventSourceARN\": \"arn:aws:dynamodb:us-east-1:123456789012:table/Example-Table/stream/2016-12-01T00:00:00.000\"\n"
			+ "    },\n"
			+ "    {\n"
			+ "      \"eventID\": \"f07f8ca4b0b26cb9c4e5e77e42f274ee\",\n"
			+ "      \"eventName\": \"INSERT\",\n"
			+ "      \"eventVersion\": \"1.1\",\n"
			+ "      \"eventSource\": \"aws:dynamodb\",\n"
			+ "      \"awsRegion\": \"us-east-1\",\n"
			+ "      \"dynamodb\": {\n"
			+ "        \"ApproximateCreationDateTime\": 1480642020,\n"
			+ "        \"Keys\": {\n"
			+ "          \"val\": {\n"
			+ "            \"S\": \"data\"\n"
			+ "          },\n"
			+ "          \"key\": {\n"
			+ "            \"S\": \"binary\"\n"
			+ "          }\n"
			+ "        },\n"
			+ "        \"NewImage\": {\n"
			+ "          \"val\": {\n"
			+ "            \"S\": \"data\"\n"
			+ "          },\n"
			+ "          \"asdf1\": {\n"
			+ "            \"B\": \"AAEqQQ==\"\n"
			+ "          },\n"
			+ "          \"b2\": {\n"
			+ "            \"B\": \"test\"\n"
			+ "          },\n"
			+ "          \"asdf2\": {\n"
			+ "            \"BS\": [\n"
			+ "              \"AAEqQQ==\",\n"
			+ "              \"QSoBAA==\",\n"
			+ "              \"AAEqQQ==\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"key\": {\n"
			+ "            \"S\": \"binary\"\n"
			+ "          },\n"
			+ "          \"Binary\": {\n"
			+ "            \"B\": \"AAEqQQ==\"\n"
			+ "          },\n"
			+ "          \"Boolean\": {\n"
			+ "            \"BOOL\": true\n"
			+ "          },\n"
			+ "          \"BinarySet\": {\n"
			+ "            \"BS\": [\n"
			+ "              \"AAEqQQ==\",\n"
			+ "              \"AAEqQQ==\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"List\": {\n"
			+ "            \"L\": [\n"
			+ "              {\n"
			+ "                \"S\": \"Cookies\"\n"
			+ "              },\n"
			+ "              {\n"
			+ "                \"S\": \"Coffee\"\n"
			+ "              },\n"
			+ "              {\n"
			+ "                \"N\": \"3.14159\"\n"
			+ "              }\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"Map\": {\n"
			+ "            \"M\": {\n"
			+ "              \"Name\": {\n"
			+ "                \"S\": \"Joe\"\n"
			+ "              },\n"
			+ "              \"Age\": {\n"
			+ "                \"N\": \"35\"\n"
			+ "              }\n"
			+ "            }\n"
			+ "          },\n"
			+ "          \"FloatNumber\": {\n"
			+ "            \"N\": \"123.45\"\n"
			+ "          },\n"
			+ "          \"IntegerNumber\": {\n"
			+ "            \"N\": \"123\"\n"
			+ "          },\n"
			+ "          \"NumberSet\": {\n"
			+ "            \"NS\": [\n"
			+ "              \"1234\",\n"
			+ "              \"567.8\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"Null\": {\n"
			+ "            \"NULL\": true\n"
			+ "          },\n"
			+ "          \"String\": {\n"
			+ "            \"S\": \"Hello\"\n"
			+ "          },\n"
			+ "          \"StringSet\": {\n"
			+ "            \"SS\": [\n"
			+ "              \"Giraffe\",\n"
			+ "              \"Zebra\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"EmptyStringSet\": {\n"
			+ "            \"SS\": []\n"
			+ "          }\n"
			+ "        },\n"
			+ "        \"SequenceNumber\": \"1405400000000002063282832\",\n"
			+ "        \"SizeBytes\": 54,\n"
			+ "        \"StreamViewType\": \"NEW_AND_OLD_IMAGES\"\n"
			+ "      },\n"
			+ "      \"eventSourceARN\": \"arn:aws:dynamodb:us-east-1:123456789012:table/Example-Table/stream/2016-12-01T00:00:00.000\"\n"
			+ "    }\n"
			+ "  ]\n"
			+ "}";

	String sampleLBEvent = "{\n"
			+ "  \"requestContext\": {\n"
			+ "    \"elb\": {\n"
			+ "      \"targetGroupArn\": \"arn:aws:elasticloadbalancing:us-east-1:XXXXXXXXXXX:targetgroup/sample/6d0ecf831eec9f09\"\n"
			+ "    }\n"
			+ "  },\n"
			+ "  \"httpMethod\": \"GET\",\n"
			+ "  \"path\": \"/\",\n"
			+ "  \"queryStringParameters\": {},\n"
			+ "  \"headers\": {\n"
			+ "    \"accept\": \"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\",\n"
			+ "    \"accept-encoding\": \"gzip\",\n"
			+ "    \"accept-language\": \"en-US,en;q=0.5\",\n"
			+ "    \"connection\": \"keep-alive\",\n"
			+ "    \"cookie\": \"name=value\",\n"
			+ "    \"host\": \"lambda-YYYYYYYY.elb.amazonaws.com\",\n"
			+ "    \"upgrade-insecure-requests\": \"1\",\n"
			+ "    \"user-agent\": \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10.11; rv:60.0) Gecko/20100101 Firefox/60.0\",\n"
			+ "    \"x-amzn-trace-id\": \"Root=1-5bdb40ca-556d8b0c50dc66f0511bf520\",\n"
			+ "    \"x-forwarded-for\": \"192.0.2.1\",\n"
			+ "    \"x-forwarded-port\": \"80\",\n"
			+ "    \"x-forwarded-proto\": \"http\"\n"
			+ "  },\n"
			+ "  \"body\": \"Hello from ELB\",\n"
			+ "  \"isBase64Encoded\": false\n"
			+ "}";

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
			"        \"accountId\": \"123456789098\",\n" +
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

	String s3Event = "{\n"
			+ "    \"Records\": [\n"
			+ "        {\n"
			+ "            \"eventVersion\": \"2.1\",\n"
			+ "            \"eventSource\": \"aws:s3\",\n"
			+ "            \"awsRegion\": \"eu-central-1\",\n"
			+ "            \"eventTime\": \"2023-11-04T23:44:23.905Z\",\n"
			+ "            \"eventName\": \"ObjectCreated:Put\",\n"
			+ "            \"userIdentity\": {\n"
			+ "                \"principalId\": \"AWS:xxxxxxxxxxxxxxxxxxx\"\n"
			+ "            },\n"
			+ "            \"requestParameters\": {\n"
			+ "                \"sourceIPAddress\": \"x.x.x.x\"\n"
			+ "            },\n"
			+ "            \"responseElements\": {\n"
			+ "                \"x-amz-request-id\": \"xxxxxxxxxxxxxxxx\",\n"
			+ "                \"x-amz-id-2\": \"xxxxxxxxxxxxxxxxxxxx\"\n"
			+ "            },\n"
			+ "            \"s3\": {\n"
			+ "                \"s3SchemaVersion\": \"1.0\",\n"
			+ "                \"configurationId\": \"xxxxxxxxxxxxxxxxxxxxxxxx\",\n"
			+ "                \"bucket\": {\n"
			+ "                    \"name\": \"xxxxxxxxxxxxxxx\",\n"
			+ "                    \"ownerIdentity\": {\n"
			+ "                        \"principalId\": \"xxxxxxxxxxxxxxxxxx\"\n"
			+ "                    },\n"
			+ "                    \"arn\": \"arn:aws:s3:::xxxxxxxxxxxxxxxxx\"\n"
			+ "                },\n"
			+ "                \"object\": {\n"
			+ "                    \"key\": \"xxxxxxxxxxxxxxxx\",\n"
			+ "                    \"size\": 6064,\n"
			+ "                    \"eTag\": \"xxxxxxxxxxxxx\",\n"
			+ "                    \"sequencer\": \"xxxxxxxxxxxxxx\"\n"
			+ "                }\n"
			+ "            }\n"
			+ "        }\n"
			+ "    ]\n"
			+ "}";

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
			"        \"accountId\": \"123456789098\",\n" +
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

	String apiGatewayEventWithArray = "{\n" +
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
			"        \"accountId\": \"123456789098\",\n" +
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
			"    \"body\":[{\"name\":\"Jim Lahey\"},{\"name\":\"Ricky\"}],\n" +
			"    \"isBase64Encoded\": false\n" +
			"}";

	String gwAuthorizerEvent = "{\n"
			+ "    \"type\":\"TOKEN\",\n"
			+ "    \"authorizationToken\":\"allow\",\n"
			+ "    \"methodArn\":\"arn:aws:execute-api:us-west-2:123456789012:ymy8tbxw7b/*/GET/\"\n"
			+ "}";

	@BeforeEach
	public void before() throws Exception {
		System.clearProperty("MAIN_CLASS");
		System.clearProperty("spring.cloud.function.routing-expression");
		System.clearProperty("spring.cloud.function.definition");
		//this.getEnvironment().clear();
	}

	@Test
	public void testScheduledEvent() throws Exception {
		System.setProperty("MAIN_CLASS", ScheduledEventConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "event");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.scheduleEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("IYV3p45BT0ac8hjHg1houSdS1a.Mro8e");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testConversionWhenPayloadExists() throws Exception {
		System.setProperty("MAIN_CLASS", BasicConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "uppercase");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.someEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result).containsKey("HEADERS");

	}

	@Test
	public void testAPIGatewayCustomAuthorizerEvent() throws Exception {
		System.setProperty("MAIN_CLASS", AuthorizerConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "acceptAuthorizerEvent");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.gwAuthorizerEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("APIGatewayCustomAuthorizerEvent(version=null, type=TOKEN");
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
	public void testCollectionPojo() throws Exception {
		System.setProperty("MAIN_CLASS", SampleConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoPojoReactive");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.jsonPojoCollection.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).isEqualTo(this.jsonPojoCollection);
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
		FunctionInvoker invoker = new FunctionInvoker() {
			@Override
			public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
				assertThat(context).isNotNull();
				super.handleRequest(input, output, context);
			}
		};

		InputStream targetStream = new ByteArrayInputStream(this.sampleKinesisEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, new TestContext());

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
	public void testDynamoDb() throws Exception {
		System.setProperty("MAIN_CLASS", DynamoDbConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "consume");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.dynamoDbEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
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
	public void testSQSEventWithConstructorArg() throws Exception {
		System.setProperty("MAIN_CLASS", SQSConfiguration.class.getName());
		FunctionInvoker invoker = new FunctionInvoker("inputSQSEvent");

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
	public void testS3EventAsOutput() throws Exception {
		System.setProperty("MAIN_CLASS", S3Configuration.class.getName());
		System.setProperty("spring.cloud.function.definition", "outputS3Event");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.s3Event.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		assertThat(output.toByteArray()).isNotNull();
	}

	@Test
	public void testS3Event() throws Exception {
		System.setProperty("MAIN_CLASS", S3Configuration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputS3Event");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.s3Event.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("ObjectCreated:Put");
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
		assertThat(result).contains("ObjectCreated:Put");
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


	@Test
	public void testLBEventStringInOut() throws Exception {
		System.setProperty("MAIN_CLASS", LBConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoString");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleLBEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("\"Hello from ELB\"");
	}

	@Test
	public void testS3EventReactive() throws Exception {
		System.setProperty("MAIN_CLASS", S3Configuration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoStringFlux");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.s3Event.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("s3SchemaVersion");
	}

	@Test
	public void testLBEvent() throws Exception {
		System.setProperty("MAIN_CLASS", LBConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputLBEvent");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleLBEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("\"Hello from ELB\"");
	}

	@Test
	public void testLBEventAsMessage() throws Exception {
		System.setProperty("MAIN_CLASS", LBConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputLBEventAsMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleLBEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, Mockito.mock(Context.class));

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("\"Hello from ELB\"");
	}

	@Test
	public void testLBEventInOut() throws Exception {
		System.setProperty("MAIN_CLASS", LBConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputOutputLBEvent");
		FunctionInvoker invoker = new FunctionInvoker() {
			@Override
			public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
				assertThat(context).isNotNull();
				super.handleRequest(input, output, context);
			}
		};

		InputStream targetStream = new ByteArrayInputStream(this.sampleLBEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, new TestContext());

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("Hello from ELB");
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
		assertThat(result.get("body")).isEqualTo("\"HELLO\"");

		System.clearProperty("spring.cloud.function.definition");
		System.setProperty("spring.cloud.function.routing-expression", "'uppercase'");
		invoker = new FunctionInvoker();
		targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		result = this.mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("\"HELLO\"");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayPojoReturninPojo() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "uppercasePojoReturnPojo");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEventWithStructuredBody.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map response = mapper.readValue(output.toByteArray(), Map.class);
		Person person = mapper.readValue((String) response.get("body"), Person.class);
		assertThat(person.getName()).isEqualTo("JIM LAHEY");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayPojoReturninPojoReactive() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "uppercasePojoReturnPojoReactive");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEventWithArray.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map response = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(response.get("body").toString()).isEqualTo("[{\"name\":\"JIM LAHEY\"},{\"name\":\"RICKY\"}]");
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
		assertThat(result.get("body")).isEqualTo("\"JIM LAHEY\"");

		System.clearProperty("spring.cloud.function.definition");
		System.setProperty("spring.cloud.function.routing-expression", "'uppercasePojo'");
		invoker = new FunctionInvoker();
		targetStream = new ByteArrayInputStream(this.apiGatewayEventWithStructuredBody.getBytes());
		output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		result = this.mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("\"JIM LAHEY\"");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayEvent() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputApiEvent");
		FunctionInvoker invoker = new FunctionInvoker() {
			@Override
			public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
				assertThat(context).isNotNull();
				super.handleRequest(input, output, context);
			}
		};

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, new TestContext());

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("\"hello\"");

		System.clearProperty("spring.cloud.function.definition");
		System.setProperty("spring.cloud.function.routing-expression", "'inputApiEvent'");
		invoker = new FunctionInvoker();
		targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		result = this.mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("\"hello\"");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayV2Event() throws Exception {
		System.out.println(this.apiGatewayV2Event);
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputApiV2Event");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayV2Event.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("\"Hello from Lambda\"");

		System.clearProperty("spring.cloud.function.definition");
		System.setProperty("spring.cloud.function.routing-expression", "'inputApiV2Event'");
		invoker = new FunctionInvoker();
		targetStream = new ByteArrayInputStream(this.apiGatewayV2Event.getBytes());
		output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		result = this.mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("\"Hello from Lambda\"");
	}

	@Test
	public void testResponseBase64Encoded() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoStringMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		JsonMapper mapper = new JacksonMapper(new ObjectMapper());

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		Map resultMap = mapper.fromJson(result, Map.class);
		assertThat((boolean) resultMap.get(AWSLambdaUtils.IS_BASE64_ENCODED)).isTrue();
		assertThat((int) resultMap.get(AWSLambdaUtils.STATUS_CODE)).isEqualTo(201);
		String body = new String(Base64.getDecoder().decode((String) resultMap.get(AWSLambdaUtils.BODY)), StandardCharsets.UTF_8);
		assertThat(body).isEqualTo("hello");
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
		assertThat(result.get("body")).isEqualTo("\"boom\"");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testApiGatewayInAndOutInputStream() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoInputStreamToString");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("hello");
		Map headers = (Map) result.get("headers");
		assertThat(headers).isNotEmpty();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testApiGatewayInAndOutInputStreamMsg() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "echoInputStreamMsgToString");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("hello");
		Map headers = (Map) result.get("headers");
		assertThat(headers).isNotEmpty();
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
		assertThat(result.get("body")).isEqualTo("\"hello\"");
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
		assertThat(result.get("body")).isEqualTo("\"hello\"");
	}

	@Test
	public void testShouldNotWrapIamPolicyResponse() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "outputPolicyResponse");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isNull();
		assertThat(result.get("principalId")).isNotNull();
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

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayWithMonoVoidAsReturn() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "reactiveWithVoidReturn");
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

		try {
			invoker.handleRequest(targetStream, output, null);
			Assertions.fail();
		}
		catch (Exception e) {
			// TODO: handle exception
		}
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
		assertThat(result.get("body")).isEqualTo("\"olleh\"");
	}

	@Test
	public void testPrimitiveMessage() throws Exception {
		System.setProperty("MAIN_CLASS", PrimitiveConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "returnByteArrayAsMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		String testString = "{ \"message\": \"Hello, world!\" }";
		InputStream targetStream = new ByteArrayInputStream(testString.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = output.toString();
		assertThat(result).isEqualTo(testString);
	}

	@EnableAutoConfiguration
	@Configuration
	public static class BasicConfiguration {
		@Bean
		public Function<Message<String>, Message<String>> uppercase() {
			return v -> {
				return MessageBuilder.withPayload(v.getPayload().toUpperCase(Locale.ROOT)).build();
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class AuthorizerConfiguration {
		@Bean
		public Function<APIGatewayCustomAuthorizerEvent, String> acceptAuthorizerEvent() {
			return v -> v.toString();
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class DynamoDbConfiguration {
		@Bean
		public Consumer<DynamodbEvent> consume() {
			return event -> event.getRecords().forEach(System.out::println);
		}
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
			return v -> v.toUpperCase(Locale.ROOT);
		}

		@Bean
		public Function<String, String> reverse() {
			return v -> new StringBuilder(v).reverse().toString();
		}

		@Bean
		public Function<Flux<String>, Flux<String>> echoStringReactive() {
			return v -> v;
		}

		@Bean
		public Function<Flux<Person>, Flux<Person>> echoPojoReactive() {
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
		public Function<S3Event, S3Event> outputS3Event() {
			return v -> {
				return v;
			};
		}
		@Bean
		public Function<String, String> echoString() {
			return v -> v;
		}

		@Bean
		public Function<Flux<String>, Flux<String>> echoStringFlux() {
			return v -> v;
		}

		@Bean
		public Function<S3Event, String> inputS3Event(JsonMapper jsonMapper) {
			return v -> {
				System.out.println("Received: " + v);
				return v.getRecords().get(0).getEventName();
			};
		}

		@Bean
		public Function<Message<S3Event>, String> inputS3EventAsMessage(JsonMapper jsonMapper) {
			return m -> {
				System.out.println("Received: " + m);
				return m.getPayload().getRecords().get(0).getEventName();
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
	public static class LBConfiguration {
		@Bean
		public Function<String, String> echoString() {
			return v -> v;
		}

		@Bean
		public Function<ApplicationLoadBalancerRequestEvent, String> inputLBEvent() {
			return v -> {
				System.out.println("Received: " + v);
				return v.getBody();
			};
		}

		@Bean
		public Function<ApplicationLoadBalancerRequestEvent, ApplicationLoadBalancerResponseEvent> inputOutputLBEvent() {
			return v -> {
				ApplicationLoadBalancerResponseEvent response = new ApplicationLoadBalancerResponseEvent();
				response.setBody(v.getBody());
				return response;
			};
		}

		@Bean
		public Function<Message<ApplicationLoadBalancerRequestEvent>, String> inputLBEventAsMessage(JsonMapper jsonMapper) {
			return message -> {
				System.out.println("Received: " + message);
				assertThat(message.getHeaders().get(AWSLambdaUtils.AWS_CONTEXT)).isNotNull();
				return message.getPayload().getBody();
			};
		}

		@Bean
		public Function<Map<String, Object>, String> inputLBEventAsMap() {
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
		public Function<Message<String>, Message<String>> echoStringMessage() {
			return m -> {
				String encodedPayload = Base64.getEncoder().encodeToString(m.getPayload().getBytes(StandardCharsets.UTF_8));
				return MessageBuilder.withPayload(encodedPayload)
						.setHeader("isBase64Encoded", true)
						.setHeader("statusCode", 201)
						.build();
			};
		}


		@Bean
		public Consumer<String> consume() {
			return v -> System.out.println(v);
		}

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase(Locale.ROOT);
		}

		@Bean
		public Function<Mono<String>, Mono<Void>> reactiveWithVoidReturn() {
			return v -> Mono.empty();
		}

		@Bean
		public Function<Person, String> uppercasePojo() {
			return v -> {
				return v.getName().toUpperCase(Locale.ROOT);
			};
		}

		@Bean
		public Function<Person, Person> uppercasePojoReturnPojo() {
			return v -> {
				Person p = new Person();
				p.setName(v.getName().toUpperCase(Locale.ROOT));
				return p;
			};
		}

		@Bean
		public Function<Flux<Person>, Flux<Person>> uppercasePojoReturnPojoReactive() {
			return flux -> flux.map(v -> {
				Person p = new Person();
				p.setName(v.getName().toUpperCase(Locale.ROOT));
				return p;
			});
		}

		@Bean
		public Function<APIGatewayProxyRequestEvent, String> inputApiEvent() {
			return v -> {
				return v.getBody();
			};
		}

		@Bean

		public Function<InputStream, String> echoInputStreamToString() {
			return is -> {
				try {
					String result = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
					return result;
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			};
		}

		@Bean

		public Function<Message<InputStream>, String> echoInputStreamMsgToString() {
			return msg -> {
				try {
					String result = StreamUtils.copyToString(msg.getPayload(), StandardCharsets.UTF_8);
					return result;
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
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

		@Bean
		public Function<Mono<String>, Mono<IamPolicyResponse>> outputPolicyResponse() {
			return input ->
				input.map(v -> IamPolicyResponse.builder()
					.withPrincipalId("principalId")
					.withPolicyDocument(IamPolicyResponse.PolicyDocument.builder()
						.withVersion("2012-10-17")
						.withStatement(
							List.of(
								IamPolicyResponse.Statement.builder().withAction("execute-api:Invoke")
									.withResource(
										List.of(v)).withEffect("Allow").build()
							)
						).build()
					).build()
				);
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class PrimitiveConfiguration {
		@Bean
		public Function<Message<byte[]>, byte[]> returnByteArrayAsMessage() {
			return v -> {
				return v.getPayload();
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

	@EnableAutoConfiguration
	@Configuration
	public static class ScheduledEventConfiguration {

		@Bean
		public Function<ScheduledEvent, ScheduledEvent> event() {
			return event -> {
				System.out.println("Event: " + event);
				return event;
			};
		}
	}
}
