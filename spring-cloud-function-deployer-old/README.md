Spring Cloud Function Deployer is an library for building apps that can deploy functions packaged as jars. It can deploy a basic Spring Cloud Function app from a jar with locally cached dependencies in about 500ms (compared to 1500ms for the same application launched from cold). It can be used in a pool as a "warm" JVM to deploy functions quicker than they could be started from scratch. Example usage:

```java
@SpringBootApplication
@EnableFunctionDeployer
public class FunctionApplication {

	public static void main(String[] args) throws IOException {
		new ApplicationBootstrap().run(FunctionApplication.class, args);
	}

}
```

There is a main class in the jar that alread looks like this. You can use it like that or you can create your own copy if you want to customize it. The `ApplicationBootstrap` is a utility that replaces `SpringApplication`, creating a class loader hierarchy that works with the function configuration. It needs to be launched with configuration for the `FunctionProperties`:

| Option | Description          |
|--------|----------------------|
| `function.location` | Mandatory archive location(s) for building the classpath of the function. |
| `function.bean`     | Mandatory bean class or name (if `function.main` is provided) to create the function. If multi-valued, the function is composed (outputs piped to inputs) |
| `function.main`     | The main `@SpringBootApplication` to launch (optional). |
