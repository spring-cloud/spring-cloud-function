# Azure Functions with DI adapter

## Common instructions to integrate Azure Functions with Spring Framework

* Use the [Spring Initializer](https://start.spring.io/) to generate a pain, java Spring Boot project without additional dependencies. Set the boot version to `2.7.x`, the build to `Maven` and the packaging to `Jar`.

* Add the `spring-cloud-function-adapter-azure` POM dependency:

	```xml
	<dependency>
		<groupId>org.springframework.cloud</groupId>
		<artifactId>spring-cloud-function-adapter-azure</artifactId>
		<version>3.2.9 or higher</version>
	</dependency>
	```
	Having the adapter on the classpath activates the Azure Java Worker integration.

* Implement the [Azure Java Functions](https://learn.microsoft.com/en-us/azure/azure-functions/functions-reference-java?tabs=bash%2Cconsumption#java-function-basics) as `@FunctionName` annotated methods:

	```java
	import java.util.Optional;
	import java.util.function.Function;

	import com.microsoft.azure.functions.ExecutionContext;
	import com.microsoft.azure.functions.HttpMethod;
	import com.microsoft.azure.functions.HttpRequestMessage;
	import com.microsoft.azure.functions.annotation.AuthorizationLevel;
	import com.microsoft.azure.functions.annotation.FunctionName;
	import com.microsoft.azure.functions.annotation.HttpTrigger;

	import org.springframework.beans.factory.annotation.Autowired;
	import org.springframework.stereotype.Component;

	@Component
	public class MyAzureFunction {

		@Autowired
		private Function<String, String> uppercase;

		@FunctionName("ditest")
		public String execute(
				@HttpTrigger(name = "req", methods = { HttpMethod.GET,
						HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
				ExecutionContext context) {

			return this.uppercase.apply(request.getBody().get());
		}
	}
	```
	- The `@FunctionName` annotated methods represent the Azure Function implementations.
	- The class must be marked with the Spring `@Component` annotation.
	- You can use any Spring mechanism to auto-wire the Spring beans used for the function implementation.

* Add the `host.json` configuration under the `src/main/resources` folder:

	```json
	{
		"version": "2.0",
		"extensionBundle": {
			"id": "Microsoft.Azure.Functions.ExtensionBundle",
			"version": "[3.*, 4.0.0)"
		}
	}
	```

* When bootstrapped as Spring Boot project make sure to either disable the `spring-boot-maven-plugin` plugin or cover it into `thin-layout`:

	```xml
	<plugin>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-maven-plugin</artifactId>
		<dependencies>
			<dependency>
				<groupId>org.springframework.boot.experimental</groupId>
				<artifactId>spring-boot-thin-layout</artifactId>
				<version>${spring-boot-thin-layout.version}</version>
			</dependency>
		</dependencies>
	</plugin>
	```
	Since Azure Functions requires a specific, custom, Jar packaging we have to disable SpringBoot one.

* Add the `azure-functions-maven-plugin` to your POM configuration. A sample configuration would look like this.

	```xml
	<plugin>
		<groupId>com.microsoft.azure</groupId>
		<artifactId>azure-functions-maven-plugin</artifactId>
		<version>1.22.0 or higher</version>

		<configuration>
			<appName>YOUR-AZURE-FUNCTION-APP-NAME</appName>
			<resourceGroup>YOUR-AZURE-FUNCTION-RESOURCE-GROUP</resourceGroup>
			<region>YOUR-AZURE-FUNCTION-APP-REGION</region>
			<appServicePlanName>YOUR-AZURE-FUNCTION-APP-SERVICE-PLANE-NAME</appServicePlanName>
			<pricingTier>YOUR-AZURE-FUNCTION-PRICING-TIER</pricingTier>

			<hostJson>${project.basedir}/src/main/resources/host.json</hostJson>

			<runtime>
				<os>linux</os>
				<javaVersion>11</javaVersion>
			</runtime>

			<funcPort>7072</funcPort>

			<appSettings>
				<property>
					<name>FUNCTIONS_EXTENSION_VERSION</name>
					<value>~4</value>
				</property>
			</appSettings>
		</configuration>
		<executions>
			<execution>
				<id>package-functions</id>
				<goals>
					<goal>package</goal>
				</goals>
			</execution>
		</executions>
	</plugin>
	```
	- Set the AZURE subscription configuration such as app name, resource group, region, service plan, pricing Tier
    - Runtime configuration:
		- [Java Versions](https://learn.microsoft.com/en-us/azure/azure-functions/functions-reference-java?tabs=bash%2Cconsumption#java-versions)
		- Specify [Deployment OS](https://learn.microsoft.com/en-us/azure/azure-functions/functions-reference-java?tabs=bash%2Cconsumption#specify-the-deployment-os)

* Build the project:

	```
	./mvnw clean package
	```

## Running Locally

NOTE: To run locally on top of `Azure Functions`, and to deploy to your live Azure environment, you will need `Azure Functions Core Tools` installed along with the Azure CLI (see [here](https://docs.microsoft.com/en-us/azure/azure-functions/create-first-function-cli-java?tabs=bash%2Cazure-cli%2Cbrowser#configure-your-local-environment)).
For some configuration you would need the [Azurite emulator](https://learn.microsoft.com/en-us/azure/storage/common/storage-use-emulator) as well.

Then build and run the sample:

```
./mvnw clean package
./mvnw azure-functions:run
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

## Debug locally

Run the function in debug mode.
```
./mvnw azure-functions:run -DenableDebug
```

Alternatively and the `JAVA_OPTS` value to your `local.settings.json` like this:

```json
{
	"IsEncrypted": false,
	"Values": {
		...
		"FUNCTIONS_WORKER_RUNTIME": "java",
		"JAVA_OPTS": "-Djava.net.preferIPv4Stack=true -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005"
	}
}
```


VS Code remote debug configuration:

	```json
	{
		"version": "0.2.0",
		"configurations": [
			{
				"type": "java",
				"name": "Attach to Remote Program",
				"request": "attach",
				"hostName": "localhost",
				"port": "5005"
			},
	}

	```