Implement a POF:

```
package functions;

import java.util.function.Function;

public class Uppercase implements Function<String, String> {

	public String apply(String input) {
		return input.toUpperCase();
	}
}
```

Install it into your local Maven repository:

```
./mvnw clean install
```

Create a `function.properties` file that provides its Maven coordinates. For example:

```
dependencies.function: io.spring.sample:uppercase-function:0.0.1-SNAPSHOT
```

Copy the openwhisk runner JAR to the working directory (same directory as the properties file):

```
cp spring-cloud-function-adapters/spring-cloud-function-adapter-openwhisk/target/spring-cloud-function-adapter-openwhisk-1.0.0.BUILD-SNAPSHOT.jar runner.jar
```

Generate a m2 repo from the `--thin.dryrun` of the runner JAR with the above properties file:

```
java -jar runner.jar --thin.root=m2 --thin.name=function --thin.dryrun
```

Use the following Dockerfile:

```
FROM openjdk:8

COPY m2 /m2
ADD runner.jar .
ADD function.properties .

ENTRYPOINT [ "sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -jar runner.jar --thin.root=/m2 --thin.name=function --function.name=uppercase" ]

EXPOSE 8080
```

Build the Docker image:

```
docker build -t [username/appname] .
```

Push the Docker image:

```
docker push [username/appname]
```

Use the OpenWhisk CLI (e.g. after `vagrant ssh`) to create the action:

```
wsk action create --docker example [username/appname]
```

Invoke the action:

```
wsk action invoke --result example --param payload foo
{
    "result": "FOO"
}
```
