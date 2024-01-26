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
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.cloudevent.CloudEventMessageBuilder;
import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.MimeTypeUtils;

@SpringBootTest
@ExtendWith(DemoApplicationTests.TestRule.class)
public class DemoApplicationTests {

	@Autowired
	private RabbitMessagingTemplate rabbitTemplate;

	ArrayBlockingQueue<Message<String>> queue = new ArrayBlockingQueue<>(1);

	@Test
	public void test() throws Exception {
		Message<byte[]> messageToAMQP = CloudEventMessageBuilder
				.withData("{\"firstName\":\"John\", \"lastName\":\"Doe\"}".getBytes())
				.setSource("https://cloudevent.demo")
				.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
				.build(CloudEventMessageUtils.AMQP_ATTR_PREFIX);

		rabbitTemplate.send("hire-in-0", "#", messageToAMQP);

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
				new CachingConnectionFactory("localhost").createConnection();
				try {
					Socket socket = new Socket();
					socket.connect(new InetSocketAddress("localhost", 9092));
					socket.close();
				}
				catch (Exception e) {
					System.out.println("Kafka is not available on localhost:9092");
					return ConditionEvaluationResult.disabled("Kafka is not available on localhost, default port");
				}
			}
			catch (Exception e) {
				System.out.println("RabbitMQ is not available on localhost:5672");
				return ConditionEvaluationResult.disabled("Rabbit is not available on localhost:5672");
			}


			return ConditionEvaluationResult.enabled("All is good");
		}
	}
}
