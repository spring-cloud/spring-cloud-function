### Introduction 

Spring Cloud Function allows you to invoke function via [gRPC](https://grpc.io/). While you can read more about gRPC in te provided link, this section will describe the parts relevant to Spring Cloud Function integration.

As with all other Spring-boot based frameworks all you need to do is add `spring-cloud-function-grpc` dependency to your POM.
```xml
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-function-grpc</artifactId>
	<version>${current.version}</version>
</dependency>
```

### Programming model
Spring Cloud Function gRPC support provides two modes of operation - _client_ and _server_. In other words when you add `spring-cloud-function-grpc` dependency to your POM you may or may not want the gRPC server as you may 
only be interested in client-side utilities to invoke a function exposed via gRPC server running on some host/port.
To support these two modes Spring Cloud Function provides `spring.cloud.function.grpc.server` which defaults to `true`.
This means that the default mode of operation is _server_, since the core imtention of gRPC support is to expose user Function via gRPC. However, if you're only inteersted in using client-side utilities (e.g., `GrpcUtils` to help to invoke a function or convert `GrpcMessage` to Spring `Message` and vice versa), you can set this property to `false`.
Hoever if you intention is to 


At the center of gRPC and Spring Cloud Function integration is a canonical protobuff structure - `GrpcMessage`. It is modeled after Spring [Message](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/Message.html).

```
message GrpcMessage {
    bytes payload = 1;
    map<string, string> headers = 2;
}
```
As you can see it is a very generic structure which can support any type of data amd metadata you wish to exchange. 

It alos defines a `MessagingService` allowing you to generate required stubs to support true plolyglot nature of gRPC. 
```
service MessagingService {
    rpc biStream(stream GrpcMessage) returns (stream GrpcMessage);
    
    rpc clientStream(stream GrpcMessage) returns (GrpcMessage);
    
    rpc serverStream(GrpcMessage) returns (stream GrpcMessage);
    
    rpc requestReply(GrpcMessage) returns (GrpcMessage);
}
```
That said, when using Java, you do not need to generate anything, rather identify function definition and send and receive Spring `Messages`.
You can get a pretty good idea from this [test case](https://github.com/spring-cloud/spring-cloud-function/blob/82e2583acd7c8aaaf2bc5ec935d486a336e97ae7/spring-cloud-function-grpc/src/test/java/org/springframework/cloud/function/grpc/GrpcInteractionTests.java#L49).

#### 4 Interaction Modes

The gRPC provides 4 interaction modes
* Reques/Repply
* Server-side streaming
* Client-side streaming
* Bi-directional streaming

Spring Cloud Function provides support for all 4 of them.


##### Request Reply
The most straight forward interaction mode is _Request/Reply_. 
Suppose you have a function

```java
@EnableAutoConfiguration
public static class SampleConfiguration {
	@Bean
	public Function<String, String> uppercase() {
		return v -> v.toUpperCase();
	}
}
```
You can invoke it using utility method(s) provided in `GrpcUtils` class
```java
Message<byte[]> message = MessageBuilder.withPayload("\"hello gRPC\"".getBytes())
					.setHeader("foo", "bar")
					.build();
Message<byte[]> reply = GrpcUtils.requestReply(message);
```