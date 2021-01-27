package io.spring.cloudevent;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.MimeTypeUtils;


@SpringBootTest(properties = {"spring.rsocket.server.port=55555"})
@ExtendWith(DemoApplicationTests.TestRule.class)
public class DemoApplicationTests {

	ArrayBlockingQueue<Message<String>> queue = new ArrayBlockingQueue<>(1000);

	@Autowired
	private RSocketRequester.Builder rsocketRequesterBuilder;

	@Test
	public void test() throws Exception {
		String payload = "{\n" +
				"    \"specversion\" : \"1.0\",\n" +
				"    \"type\" : \"org.springframework\",\n" +
				"    \"source\" : \"https://spring.io/\",\n" +
				"    \"id\" : \"A234-1234-1234\",\n" +
				"    \"datacontenttype\" : \"application/json\",\n" +
				"    \"data\" : {\n" +
				"        \"firstName\" : \"John\",\n" +
				"        \"lastName\" : \"Doe\"\n" +
				"    }\n" +
				"}";

		this.rsocketRequesterBuilder.tcp("localhost", 55555)
			.route("hire")
			.metadata("{\"content-type\":\"application/cloudevents+json\"}", MimeTypeUtils.APPLICATION_JSON)
			.data(payload)
			.send()
			.subscribe();

		Message<String> resultFromKafka = queue.poll(2000, TimeUnit.MILLISECONDS);
		System.out.println("Result Message: " + resultFromKafka);
		System.out.println("Cloud Event 'specversion': " + CloudEventMessageUtils.getSpecVersion(resultFromKafka));
		System.out.println("Cloud Event 'source': " + CloudEventMessageUtils.getSource(resultFromKafka));
		System.out.println("Cloud Event 'id': " + CloudEventMessageUtils.getId(resultFromKafka));
		System.out.println("Cloud Event 'type': " + CloudEventMessageUtils.getType(resultFromKafka));
	}

	@KafkaListener(id = "test", topics = "hire-out-0", clientIdPrefix = "cloudEvents")
    public void listen(Message<String> message) {
		queue.add(message);
    }

	public static class TestRule implements ExecutionCondition {
		@Override
		public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
			try {
				Socket socket = new Socket();
				socket.connect(new InetSocketAddress("localhost", 9092));
				socket.close();
			}
			catch (Exception e) {
				System.out.println("Kafka is not available on localhost:9092");
				return ConditionEvaluationResult.disabled("Kafka is not available on localhost, default port");
			}

			return ConditionEvaluationResult.enabled("All is good");
		}
	}
}
