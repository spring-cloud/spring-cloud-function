package com.example.grpc.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.grpc.MessagingServiceGrpc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ProtocolStringList;

import io.cloudevents.v1.CloudEventServiceGrpc;
import io.cloudevents.v1.proto.CloudEvent;
import io.cloudevents.v1.proto.CloudEvent.CloudEventAttributeValue;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@SpringBootApplication
public class DemoGrpcApplication  {

	public static void main(String[] args) throws Exception {

		SpringApplication.run(DemoGrpcApplication.class,
				"--spring.cloud.function.grpc.service-class-name=org.springframework.cloud.function.grpc.ce.CloudEventHandler");

		CloudEvent cloudEvent = CloudEvent.newBuilder()
				.setTextData("{\"event_name\":\"SCF supports CloudEvent gRPC\"}")
				.setSource("http://springsource.com")
				.setId("12345")
				.setSpecVersion("1.0")
				.setType("org.springframework")
				.putAttributes("name", CloudEventAttributeValue.newBuilder().setCeString("oleg").build())
				.putAttributes("fluent_in_french", CloudEventAttributeValue.newBuilder().setCeBoolean(false).build())
				.build();

		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6048)
			.usePlaintext().build();

		CloudEventServiceGrpc.CloudEventServiceBlockingStub stub = CloudEventServiceGrpc.newBlockingStub(channel);
		CloudEvent reply = stub.requestReply(cloudEvent);
		System.out.println(reply);

	}

	@Bean
	public Function<Message<String>, Message<String>> uppercase() {
		return message -> {
			return MessageBuilder.withPayload(message.getPayload().toUpperCase())
					.copyHeaders(message.getHeaders())
					.setHeader("uppercased", "true")
					.build();
		};
	}
}


