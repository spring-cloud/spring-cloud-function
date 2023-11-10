# Introduction

This example shows GraaalVM native spring-cloud-function application. The application itself is very simple and contains two functions - `uppercase` & `lowercase`.
Unless specific value is specified as `handler`, the application will fall back on `RoutingFunction` where you can pass the routing instruction 
via `spring.cloud.function.definition` Message header. If using API Gateway you can pass such header as HTTP header.

# To run

## If you are on OSX Apple M1 Pro (arch64)

You first need to build a Docker image where you will actually build project.
To do that execute the following command form [project directory

```
docker build -t "al2-graalvm19:native-uppercase" .
```
Start the container

```
docker run -dit -v `pwd`:`pwd` -w `pwd` -v ~/.m2:/root/.m2 al2-graalvm19:native-uppercase
```
 
Now navigate to the image terminal. Your working directory is alredy set for the root of the project. You can verify it by executing `ls`.

Build the project:

```
./mvnw clean -Pnative native:compile -DskipTests
```

Once the build finishes, you can deploy it. 

## Deploying to AWS LAmbda

Start *AWS Dashboard* and navigate to **AWS Lambda** Services

Click on `Create Function`.  Enter `uppercase` for *function name*. For the runtime select `Provide your own bootstrap on Amazon Linux 2`. 
Make sure you select the proper architecture (`x86_64` or `arm64`). 

Click on `Create Function` again.

Next you need to upload your project, so click on `Upload From` and point to the ZIP file that was created by the build process (in the `target` directory).

Once the file is uploaded navigate to the `Test` tab. You can change the input data or use the default. Basically you need to pas a String in a JSON format such as `"hello"` and you should see the output `"HELLO"`. 
