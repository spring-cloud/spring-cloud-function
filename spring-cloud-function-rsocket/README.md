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

### Messaging

If you want to provide and/or receive additional information that you would normally communicate via Message headers you can send and receive Spring `Message`.
For example, the following tests case demonstrates how you can accomplish that.
```
Person p = new Person();
p.setName("Ricky");
Message<Person> message = MessageBuilder.withPayload(p).setHeader("someHeader", "foo").build();

Message<Person> result = rsocketRequesterBuilder.tcp("localhost", port)
	.route("pojoMessageToPojo")
	.data(message)
	.retrieveMono(new ParameterizedTypeReference<Message<Employee>>() {})
	.block();
```
Aside from sending `Message`, note the usage of `ParameterizedTypeReference` to specify that we want not only `Message` in return but also `Message` with specific payload type. 

### Order of priority for routing instructions

As you can see from the preceding example, we provide function definition as a value to `route(..)` operator of `RSocketRequester.Builder`.
However that is not the only way. You can also use standard `spring.cloud.function.definition` property as well as `spring.cloud.function.routing-expression` or property or `MessageRoutingCallback` on the server side of the RSocket interaction (see "Function Routing and Filtering" section of reference manual). 
This raises a question of _order_ and _priorities_ when it comes to reconsiling a conflict in the event several ways of providing definition are used. So it is a mater of clearly stating the rule whcih is:

***1 - MessageRoutingCallback***
The `MessageRoutingCallback` takes precedence over all other ways of providing function definition resolution.

***2 - spring.cloud.function.routing-expression***
The `spring.cloud.function.routing-expression` property takes next precedence. So, in the event you may have also use `route(..)` operator or `spring.cloud.function.definition` property, they will be ignored if `spring.cloud.function.routing-expression` property is provided.

***3 - route(..)***
The next in line is `route(..)` operator. So in the event there are no `spring.cloud.function.routing-expression` property but you defined `spring.cloud.function.definition` property, it will be ignored in favor of definition provided by the `route(..)` operator.

***4 - spring.cloud.function.definition***
The `spring.cloud.function.definition` property is the last in the list allowing you to simply `route("")` to empty string.



You can also look at one of the [RSocket samples](https://github.com/spring-cloud/spring-cloud-function/tree/master/spring-cloud-function-samples/function-sample-cloudevent-rsocket) that is also introduces you to Cloud Events 