### Introduction 

Spring Cloud Function allows you to invoke function via [RSocket](https://rsocket.io/). While you can read more about RSocket and it’s java 
implementation [here](https://github.com/rsocket/rsocket-java), this section will describe the parts relevant to Spring Cloud Function integration.

### Programming model
From the user perspective bringing RSocket does not change the implementation of functions or any of its features, such as type conversion, 
composition, POJO functions etc.
And while RSocket allows first class reactive interaction over the network supporting important reactive features such as back pressure, 
users of Spring Cloud Function still have freedom to implement their business logic using reactive or imperative functions delegating any 
adjustment needed to apply proper invocation model to the framework.

To use RSocket integration all you need is to add `spring-cloud-function-rsocket` dependency to your classpath
```
<dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-function-rsocket</artifactId>
            <version>3.1.0</version>
 </dependency>
```

To interact with functions via RSocket we rely on Spring Boot support for RSocket and `RSocketRequester.Builder` API.
The code below shows the key parts and you can get more details on various interaction models 
from [this test case](https://github.com/spring-cloud/spring-cloud-function/blob/master/spring-cloud-function-rsocket/src/test/java/org/springframework/cloud/function/rsocket/RSocketAutoConfigurationTests.java).


```
@Bean
public Function<String, String> uppercase() {
	return v -> v.toUpperCase();
}

. . .

RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

rsocketRequesterBuilder.tcp("localhost", port)
	.route(“uppercase")
	.data("\"hello\"")
	.retrieveMono(String.class)
	.subscribe(System.out::println);
```

Once connected to RSocket we use `route` operation to specify which function we want to invoke providing the actual 
payload via `data` operation. Then we use one of the `retrieve` operations that best suits our desired interaction 
(RSocket supports multiple interaction models such as fire-and-forget, request-reply etc.)

If you want to provide additional information that you would normally communicate via Message headers, you can use `metadata` operation for that.
```
rsocketRequesterBuilder.tcp("localhost", port)
	.route(“uppercase”)
	.metadata("{\”header_key\":\”header-value\"}", MimeTypeUtils.APPLICATION_JSON)		
	.data("\"hello\"")
	.retrieveMono(String.class)
	.subscribe(System.out::println);
```

You can also look at one of the [RSocket samples](https://github.com/spring-cloud/spring-cloud-function/tree/master/spring-cloud-function-samples/function-sample-cloudevent-rsocket) that is also introduces you to Cloud Events 