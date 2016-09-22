Example:

```
java -jar spring-cloud-function-stream-1.0.0.BUILD-SNAPSHOT.jar --server.port=8081 --spring.cloud.stream.bindings.input.destination=words --spring.cloud.stream.bindings.output.destination=uppercaseWords --function.name=uppercase --function.code="f -> f.map(s -> s.toString().toUpperCase())"
```

(more docs soon)
