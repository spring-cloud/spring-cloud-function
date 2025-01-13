In this sample, you'll build a native GraalVM image with `spring-cloud-function` and set it up to run in AWS Lambda.

The sample contains two functions - `uppercase` and `reverse` - so you can see how to route requests. A provided `RoutingFunction` will send messages to a handler function specified in a header named: `spring.cloud.function.definition` (demonstrated in the test section). The routing value can also be passed as an environment variable. If using API Gateway, you can pass this value as an HTTP header. 

**Example function definition**
```
@Bean
public Function<String, String> uppercase() {
  return v -> {
    System.out.println("Uppercasing " + v);
    return v.toUpperCase();
  };
}
```

> Note: If your function takes a Spring Message as an input parameter (e.g., Function<Message, ..>), the Lambda Context object will be available in the message header `aws-context`. See [AWSLambdaUtils.java](https://github.com/spring-cloud/spring-cloud-function/blob/main/spring-cloud-function-adapters/spring-cloud-function-adapter-aws/src/main/java/org/springframework/cloud/function/adapter/aws/AWSLambdaUtils.java#L67C44-L67C55) for details.


## To build the sample on macOS (Apple silicon arm64)

You first need to build the function, then you will deploy it to AWS Lambda.

### Step 1 - Build the native image

Before starting the build, you must clone or download the code in **function-sample-aws-native**.

1. Change into the project directory: `spring-cloud-function-samples/function-sample-aws-native`
2. Run the following to build a Docker container image which will be used to create the Lambda function zip file. 
   ```
   docker build -t "al2-graalvm19:native-function" .
   ```
3. Start the container
   ```
   docker run -dit -v `pwd`:`pwd` -w `pwd` -v ~/.m2:/root/.m2 al2-graalvm19:native-function 
   ```
   
   or 
   
   ```
   docker run -dit -v $(pwd):$(pwd) -w $(pwd) -v ~/.m2:/root/.m2 al2-graalvm19:native-function
   ```
4. In Docker, open the image terminal. 

   > Your working directory should default to the project root. Verify by running `ls` to view the files.

6. From inside the container, build the Lambda function:
   ```
   ./mvnw clean -Pnative native:compile -DskipTests
   ```

After the build finishes, you need to deploy the function.


### Step 2 - Deploy your function

You will first create the function, and then you will upload the zipped native image from the build process.

**Create the function**
1. Login to the **Amazon Web Services console**.
2. Navigate to the **Lambda service**.
3. Choose `Create Function`.
4. For **function name**, enter `native-func-sample`.
5. For runtime, select `Provide your own bootstrap on Amazon Linux 2`.
6. For architecture, select `arm64`.
7. Choose `Create Function` again.

**Upload the zip image**
1. Choose `Upload from`, then `.zip file`.
2. From the `target` directory, select the .zip file created by the build.
3. Wait for the image to upload.

### Step 3 - Test your function

Your test event will provide the information needed to select the `uppercase` or `reverse` handler functions.

1. From the Lambda console, navigate to the `Test` tab. 
2. For test data, enter the following JSON:
   ```JSON
   {
       "payload": "hello",
       "headers": {
           "spring.cloud.function.definition": "uppercase"
       }
   }
   ```
3. Choose **Test**.
   You should see uppercased output for the payload value: "HELLO" 

4. Change the test data to the following JSON:
   ```JSON
   {
       "payload": "hello",
       "headers": {
           "spring.cloud.function.definition": "reverse"
       }
   }
   ```
5. Choose **Test**.
   You should see reversed output for the payload value: "olleh" 


**Congratulations!** You have built and deployed a Graal native image to AWS Lambda.  
