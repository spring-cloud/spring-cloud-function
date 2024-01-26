In this sample, you'll build a native GraalVM image for running web workloads in AWS Lambda.


## To build the sample on macOS (Apple silicon arm64)

You first need to build the function, then you will deploy it to AWS Lambda.

### Step 1 - Build the native image

Before starting the build, you must clone or download the code in **function-sample-aws-native**.

1. Change into the project directory: `spring-cloud-function-samples/function-sample-aws-native`
2. Run the following to build a Docker container image which will be used to create the Lambda function zip file. 
   ```
   docker build -t "al2-graalvm21:native-function" .
   ```
3. Start the container
   ```
   docker run -dit -v `pwd`:`pwd` -w `pwd` -v ~/.m2:/root/.m2 al2-graalvm21:native-function
   ```
4. In Docker, open the image terminal. 

   > Your working directory should default to the project root. Verify by running `ls` to view the files.

6. From inside the container, build the Lambda function:
   ```
   ./mvnw clean -Pnative native:compile -DskipTests
   ```

After the build finishes, you need to deploy the function.

