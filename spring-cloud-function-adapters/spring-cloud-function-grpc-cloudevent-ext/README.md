# Spring Cloud Function gRPC extension to support CloudEvent proto.

This extension project designed as an extension to general Spring Cloud Function gRPC support to specifically suport
[CloudEvent proto](https://github.com/cloudevents/spec/blob/v1.0.1/spec.proto)

To use it simply import it as a dependency to your project together with 

```xml
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-function-grpc</artifactId>
</dependency>
```

Your project should also explicitly import [CloudEvent proto](https://github.com/cloudevents/spec/blob/v1.0.1/spec.proto) and 
service proto

```
syntax = "proto3";

package io.cloudevents.v1;

import "google/protobuf/any.proto";
import "google/protobuf/timestamp.proto";
import "CloudEvent.proto";

service CloudEventService {
    rpc biStream(stream io.cloudevents.v1.CloudEvent) returns (stream io.cloudevents.v1.CloudEvent);
    
    rpc clientStream(stream io.cloudevents.v1.CloudEvent) returns (io.cloudevents.v1.CloudEvent);
    
    rpc serverStream(io.cloudevents.v1.CloudEvent) returns (stream io.cloudevents.v1.CloudEvent);
    
    rpc requestReply(io.cloudevents.v1.CloudEvent) returns (io.cloudevents.v1.CloudEvent);
}
```

Once done, you can send/receive CloudEvent messages 

You can also reference [this sample](https://github.com/spring-cloud/spring-cloud-function/tree/main/spring-cloud-function-samples/function-sample-grpc-cloudevent)
