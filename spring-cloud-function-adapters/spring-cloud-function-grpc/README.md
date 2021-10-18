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

#### Two operation modes (client/server)
Spring Cloud Function gRPC support provides two modes of operation - _client_ and _server_. In other words when you add `spring-cloud-function-grpc` dependency to your POM you may or may not want the gRPC server as you may 
only be interested in client-side utilities to invoke a function exposed via gRPC server running on some host/port.
To support these two modes Spring Cloud Function provides `spring.cloud.function.grpc.server` which defaults to `true`.
This means that the default mode of operation is _server_, since the core intention of our current gRPC support is to expose user Functions via gRPC. However, if you're only inteersted in using client-side utilities (e.g., `GrpcUtils` to help to invoke a function or convert `GrpcMessage` to Spring `Message` and vice versa), you can set this property to `false`.

In the server (default) mode, te gRPC server would be bound to te default port ***6048***. You can change it by providing 
`spring.cloud.function.grpc.port` property.

#### Core Data and Service
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

#### 4 Interaction RPC Modes

The gRPC provides 4 interaction modes
* Reques/Repply RPC
* Server-side streaming RPC
* Client-side streaming RPC
* Bi-directional streaming RPC

Spring Cloud Function provides support for all 4 of them.

##### Request Reply RPC
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
After identifying this function via `spring.cloud.function.definition` property (see example [here](https://github.com/spring-cloud/spring-cloud-function/blob/ded02fec0a6d3d66b8ec00f99f28be2a4bbec668/spring-cloud-function-grpc/src/test/java/org/springframework/cloud/function/grpc/GrpcInteractionTests.java)), 
you can invoke it using utility method(s) provided in `GrpcUtils` class

```java
Message<byte[]> message = MessageBuilder.withPayload("\"hello gRPC\"".getBytes())
			.setHeader("foo", "bar")
			.build();
Message<byte[]> reply = GrpcUtils.requestReply(message);
```

You can also provide `spring.cloud.function.definition` property via `Message` headers, to support more dynamic cases.

```java
Message<byte[]> message = MessageBuilder.withPayload("\"hello gRPC\"".getBytes())
			.setHeader("foo", "bar")
			.setHeader("spring.cloud.function.definition", "reverse")
			.build();
```

##### Server-side streaming RPC
The Server-side streaming RPC allows you to reply with the stream of data.

```java
@EnableAutoConfiguration
public static class SampleConfiguration {
	@Bean
	public Function<String, Flux<String>> stringInStreamOut() {
		return value -> Flux.just(value, value.toUpperCase());
	}
}
```
After identifying this function via `spring.cloud.function.definition` property (see example [here](https://github.com/spring-cloud/spring-cloud-function/blob/ded02fec0a6d3d66b8ec00f99f28be2a4bbec668/spring-cloud-function-grpc/src/test/java/org/springframework/cloud/function/grpc/GrpcInteractionTests.java)), 
you can invoke it using utility method(s) provided in `GrpcUtils` class

```java
Message<byte[]> message = MessageBuilder.withPayload("\"hello gRPC\"".getBytes()).setHeader("foo", "bar").build();

Flux<Message<byte[]>> reply =
		GrpcUtils.serverStream("localhost", FunctionGrpcProperties.GRPC_PORT, message);

List<Message<byte[]>> results = reply.collectList().block(Duration.ofSeconds(5));
```

You can see that gRPC stream is mapped to instance of `Flux` from [project reactor](https://projectreactor.io/)

Similarly to the _request/reply_ you can also provide `spring.cloud.function.definition` property via `Message` headers, to support more dynamic cases.

```java
Message<byte[]> message = MessageBuilder.withPayload("\"hello gRPC\"".getBytes())
			.setHeader("foo", "bar")
			.setHeader("spring.cloud.function.definition", "reverse")
			.build();
```

##### Client-side streaming RPC
The Client-side streaming RPC allows you to stream input data and receive a single reply.

```java
@EnableAutoConfiguration
public static class SampleConfiguration {
	@Bean
	public Function<Flux<String>, String> streamInStringOut() {
		return flux -> flux.doOnNext(v -> {
			try {
				// do something useful
				Thread.sleep(new Random().nextInt(2000)); // artificial delay
			}
			catch (Exception e) {
				// ignore
			}
		}).collectList().block().toString();
	}
}
```
After identifying this function via `spring.cloud.function.definition` property (see example [here](https://github.com/spring-cloud/spring-cloud-function/blob/ded02fec0a6d3d66b8ec00f99f28be2a4bbec668/spring-cloud-function-grpc/src/test/java/org/springframework/cloud/function/grpc/GrpcInteractionTests.java)), 
you can invoke it using utility method(s) provided in `GrpcUtils` class

```java
List<Message<byte[]>> messages = new ArrayList<>();
messages.add(MessageBuilder.withPayload("\"Ricky\"".getBytes()).setHeader("foo", "bar")
		.build());
messages.add(MessageBuilder.withPayload("\"Julien\"".getBytes()).setHeader("foo", "bar")
		.build());
messages.add(MessageBuilder.withPayload("\"Bubbles\"".getBytes()).setHeader("foo", "bar")
		.build());

Message<byte[]> reply =
		GrpcUtils.clientStream("localhost", FunctionGrpcProperties.GRPC_PORT, Flux.fromIterable(messages));

```

You can see that gRPC stream is mapped to instance of `Flux` from [project reactor](https://projectreactor.io/)

Unlike the _request/reply_ and _server-side streaming_, you can ONLY pass function definition via property or environment variable.

##### Bi-Directional streaming RPC
The bi-directional streaming RPC allows you to stream input and output data.

```java
@EnableAutoConfiguration
public static class SampleConfiguration {
	@Bean
	public Function<Flux<String>, Flux<String>> uppercaseReactive() {
		return flux -> flux.map(v -> v.toUpperCase());
	}
}
```
After identifying this function via `spring.cloud.function.definition` property (see example [here](https://github.com/spring-cloud/spring-cloud-function/blob/ded02fec0a6d3d66b8ec00f99f28be2a4bbec668/spring-cloud-function-grpc/src/test/java/org/springframework/cloud/function/grpc/GrpcInteractionTests.java)), 
you can invoke it using utility method(s) provided in `GrpcUtils` class

```java
List<Message<byte[]>> messages = new ArrayList<>();
messages.add(MessageBuilder.withPayload("\"Ricky\"".getBytes()).setHeader("foo", "bar")
		.build());
messages.add(MessageBuilder.withPayload("\"Julien\"".getBytes()).setHeader("foo", "bar")
		.build());
messages.add(MessageBuilder.withPayload("\"Bubbles\"".getBytes()).setHeader("foo", "bar")
		.build());

Flux<Message<byte[]>> clientResponseObserver =
		GrpcUtils.biStreaming("localhost", FunctionGrpcProperties.GRPC_PORT, Flux.fromIterable(messages));

List<Message<byte[]>> results = clientResponseObserver.collectList().block(Duration.ofSeconds(1));
```

You can see that gRPC stream is mapped to instance of `Flux` from [project reactor](https://projectreactor.io/)

Unlike the _request/reply_ and _server-side streaming_, you can ONLY pass function definition via property or environment variable.

#### Pluggable protobuf extension 

While the core data object and its corresponding schema <<Core-Data-and-Service>> are modeled after Spring Message and can represent 
virtually any object, there are times when you may want to plug-in your own protobuf services. 

Spring Cloud Function provides such support by allowing you to develop extensions, which once exist could be enabled by simply 
including its dependency in the POM. Such extensions are just another spring-boot project that has dependency on `spring-cloud-function-grpc`

```xml
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-function-grpc</artifactId>
</dependency>
```

It must also contain 3 classes;  1) Its configuration class, 2) Type converter for the actual protobuf 'message'and 3) Service handler
where you would normally implement your handling functionality. However instead of implementing full functionality you can model your service 
after MessagingService provided by us and if you do you can rely on the existing implementation of the core interaction models provided by gRPC

In fact Spring Cloud Function provides one of such extensions to support [Cloud Events](https://github.com/spring-cloud/spring-cloud-function/tree/main/spring-cloud-function-adapters/spring-cloud-function-grpc-cloudevent-ext) proto, so you can model yours after it.

#### Multiple services on classpath

With the protobuf extension mentioned in the previous section you may very well end up with several services on the classpath. 
By default each available service will be enabled. However, if your intention is to only use one, you can specify which one by providing 
its class name via `spring.cloud.function.grpc.service-class-name` property:

```
--spring.cloud.function.grpc.service-class-name=org.springframework.cloud.function.grpc.ce.CloudEventHandler
```