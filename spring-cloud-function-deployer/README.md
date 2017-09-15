Spring Cloud Function Deployer is an app that can deploy functions packaged as jars. Once the app is running it can deploy a basic Spring Cloud Function app from a jar with locally cached dependencies in about 500ms (compared to 1500ms for the same application launched from cold). It can be used in a pool as a "warm" JVM to deploy functions quicker than they could be started from scratch.

The app has a single endpoint called "/admin" that you can use to manage the deployed functions. You GET from it to list the deployed apps, POST to `/{name}` to deploy a named app with a `path` parameter pointing to a jar resource, and then DELETE `/{name}` to remove it. Functions in the apps are exposed as `/{name}/{function}` with the usual conventions for Spring Cloud Function (i.e. the function name is the bean name by default).

== Running the Deployer

Run the main class `ApplicationRunner` in this project (from the command line or in the IDE). E.g.

```
$ ./mvnw install -DskipTests
$ cd spring-cloud-function-deployer
$ ../mvnw spring-boot:run
```

The app starts empty, so the admin resource shows no deployed apps:

```
$ curl localhost:8080/admin
{}
```

Deploy a sample like this:

```
$ curl localhost:8080/admin/pojos -d path=maven://com.example:function-sample-pojo:1.0.0.BUILD-SNAPSHOT
{"id":"81c568e36c7909ec1dd841aa7ee6d3e3"}
```

(takes about 500ms, once the local Maven cache is warm). Deploy another one:

```
$ curl localhost:8080/admin/sample -d path=maven://com.example:function-sample:1.0.0.BUILD-SNAPSHOT
{"id":"cb2fdb3130f6349f143f4686848ea90f"}
```

Undeploy the first one:

```
$ curl localhost:8080/admin/pojos -X DELETE
{"name":"81c568e36c7909ec1dd841aa7ee6d3e3","id":"pojos","path":"maven://com.example:function-sample-pojo:1.0.0.BUILD-SNAPSHOT"}
```

List the deployed apps:

```
$ curl localhost:8080/admin
{"sample":{"name":"sample","id":"cb2fdb3130f6349f143f4686848ea90","path":"maven://com.example:function-sample:1.0.0.BUILD-SNAPSHOT"}}
```

Send an event to one of the functions:

```
$ curl -H "Content-Type: text/plain" localhost:8080/sample/uppercase -d foo
FOO
```

