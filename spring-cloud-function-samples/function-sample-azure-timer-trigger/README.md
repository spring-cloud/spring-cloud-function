# Azure TimerTrigger Function

Spring Cloud Function example for implementing [Timer trigger for Azure Functions](https://learn.microsoft.com/en-us/azure/azure-functions/functions-bindings-timer?tabs=in-process&pivots=programming-language-java).

NOTE: JVM '17' is required.

https://learn.microsoft.com/en-us/azure/azure-functions/functions-bindings-timer?tabs=in-process&pivots=programming-language-java

## Running Locally

NOTE: To run locally on top of Azure Functions, and to deploy to your live Azure environment, you will need Azure Functions Core Tools installed along with the Azure CLI (see [here](https://docs.microsoft.com/en-us/azure/azure-functions/create-first-function-cli-java?tabs=bash%2Cazure-cli%2Cbrowser#configure-your-local-environment) for details) as well as the Use [Azurite emulator](https://learn.microsoft.com/en-us/azure/storage/common/storage-use-emulator) for local Azure Storage development. For the emulator you can run a docker container (see below) or use the [Visual-Studio-Code extension](https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite?tabs=visual-studio-code). 

```
docker run --name azurite --rm -p 10000:10000 -p 10001:10001 -p 10002:10002 mcr.microsoft.com/azure-storage/azurite
```

```
./mvnw clean package
./mvnw azure-functions:run
```

The timer triggers the function every minute. 
In result the the `uppercase` Spring Cloud Function is called and uppercase the timeInfo and logs it into the context.

```
[2022-10-11T08:53:00.011Z] Timer is triggered: {"Schedule":{"AdjustForDST":true},"ScheduleStatus":{"Last":"2022-10-11T10:52:00.003967+02:00","Next":"2022-10-11T10:53:00+02:00","LastUpdated":"2022-10-11T10:52:00.003967+02:00"},"IsPastDue":false}
```

## Running on Azure

Make sure you are logged in your Azure account.
```
az login
```

Build and deploy

```
./mvnw clean package
./mvnw azure-functions:deploy
```

## Implementation details

The `uppercase` function signature is `Function<Message<String>, Void> uppercase()`. The implementation of `UppercaseHandler` (which extends `FunctionInvoker`) provides access to the Azure Function context via the _MessageHeaders_.

NOTE: Implementation of `FunctionInvoker` (your handler), should contain the least amount of code. It is really a type-safe way to define 
and configure function to be recognized as Azure Function. 
Everything else should be delegated to the base `FunctionInvoker` via `handleRequest(..)` callback which will invoke your function, taking care of 
necessary type conversion, transformation etc. One exception to this rule is when custom result handling is required. In that case, the proper post-process method can be overridden as well in order to take control of the results processing.

`UppercaseHandler.java`:

```java
public class UppercaseHandler extends FunctionInvoker<Message<String>, Void> {

    @FunctionName("uppercase")
    public void execute(@TimerTrigger(name = "keepAliveTrigger", schedule = "0 */1 * * * *") String timerInfo,
            ExecutionContext context) {

        Message<String> message = MessageBuilder.withPayload(timerInfo).build();
        
        handleRequest(message, context);
    }
}
```

Note that this function does not return value (e.g. Void output type) and is backed by `java.util.Consumer` SCF implementation:

```java
	@Bean
	public Consumer<Message<String>> uppercase() {
		return message -> {
			// /timeInfo is a JSON string, you can deserialize it to an object using your favorite JSON library
			String timeInfo = message.getPayload();

			// Business logic -> convert the timeInfo to uppercase.
			String value = timeInfo.toUpperCase();
			
			// (Optionally) access and use the Azure function context.
			ExecutionContext context = (ExecutionContext) message.getHeaders().get("executionContext");
			context.getLogger().info("Timer is triggered with TimeInfo: " + value);

			// No response.
		};
	}
```

## Notes

* Disable the `spring-boot-maven-plugin` in favor of the `azure-functions-maven-plugin`.
* Exclude the `org.springframework.boot:spring-boot-starter-logging` dependency from the `org.springframework.cloud:spring-cloud-function-adapter-azure`.
* Add `"AzureWebJobsStorage": "UseDevelopmentStorage=true"` to the `local.settings.json`.
