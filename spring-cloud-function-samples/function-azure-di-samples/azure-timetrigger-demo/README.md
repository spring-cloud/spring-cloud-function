# Azure TimerTrigger Function

Spring Cloud Function example for implementing [Timer trigger for Azure Functions](https://learn.microsoft.com/en-us/azure/azure-functions/functions-bindings-timer?tabs=in-process&pivots=programming-language-java).

## Running Locally

NOTE: To run locally on top of `Azure Functions`, and to deploy to your live Azure environment, you will need `Azure Functions Core Tools` installed along with the Azure CLI (see [here](https://docs.microsoft.com/en-us/azure/azure-functions/create-first-function-cli-java?tabs=bash%2Cazure-cli%2Cbrowser#configure-your-local-environment)) as well as the Use [Azurite emulator](https://learn.microsoft.com/en-us/azure/storage/common/storage-use-emulator) for local Azure Storage development. For the emulator you can run a docker container (see below) or use the [Visual-Studio-Code extension](https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite?tabs=visual-studio-code). 

Here is how ot start the `Azure emulator` as docker container:

```
docker run --name azurite --rm -p 10000:10000 -p 10001:10001 -p 10002:10002 mcr.microsoft.com/azure-storage/azurite
```

Then build and run the sample:

```
./mvnw clean package
./mvnw azure-functions:run
```

The timer triggers the function every minute. 
In result the the `uppercase` Spring Cloud Function is called and uppercase the timeInfo and logs it into the context.

```
[2022-10-11T08:53:00.011Z] Execution Context Log - TimeInfo: {"Schedule":{"AdjustForDST":true},"ScheduleStatus":{"Last":"2022-10-11T10:52:00.003967+02:00","Next":"2022-10-11T10:53:00+02:00","LastUpdated":"2022-10-11T10:52:00.003967+02:00"},"IsPastDue":false}
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

The `spring-cloud-function-adapter-azure` dependency activates the AzureFunctionInstanceInjector:

```xml
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-function-adapter-azure</artifactId>
	<version>3.2.9-SNAPSHOT</version>
</dependency>
```

(Version 3.2.9 or higher)


The `uppercase` function with signature `Function<Message<String>, Void> uppercase()` is defined as `@Bean` in the TimeTriggerDemoApplication context. 


```java
	@Bean
	public Consumer<Message<String>> uppercase() {
		return message -> {
			String timeInfo = message.getPayload();
			String value = timeInfo.toUpperCase();

			logger.info("Timer is triggered with TimeInfo: " + value);

			// (Optionally) access and use the Azure function context.
			ExecutionContext context = (ExecutionContext) message.getHeaders().get(UppercaseHandler.EXECUTION_CONTEXT);
			context.getLogger().info("Execution Context Log - TimeInfo: " + value);

			// No response.
		};
	}
```

TIP: The uppercase function does not return value (e.g. Void output type) and is backed by `java.util.Consumer`.

The `UppercaseHandler` (marked as Spring `@Component`) implements the Azure function using the Azure Function Java API. Furthermore as Spring component the UppercaseHandler leverages the Spring configuration and programming model to inject the necessary services required by the functions.

```java
@Component
public class UppercaseHandler {

    public static String EXECUTION_CONTEXT = "executionContext";

    @Autowired
    private Consumer<Message<String>> uppercase;

    @FunctionName("uppercase")
    public void execute(@TimerTrigger(name = "keepAliveTrigger", schedule = "0 */1 * * * *") String timerInfo,
            ExecutionContext context) {

        Message<String> message = MessageBuilder
                .withPayload(timerInfo)
                .setHeader(EXECUTION_CONTEXT, context)
                .build();

        this.uppercase.accept(message);
    }
}
```


## Notes

* Change the `spring-boot-maven-plugin` to `tiny` in favor of the `azure-functions-maven-plugin` jar packaging. 
* Add `"AzureWebJobsStorage": "UseDevelopmentStorage=true"` to the `local.settings.json`.
