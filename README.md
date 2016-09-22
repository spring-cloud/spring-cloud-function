Example Stream Application:

```
> java -jar spring-cloud-function-stream-1.0.0.BUILD-SNAPSHOT.jar
        --server.port=8081
        --spring.cloud.stream.bindings.input.destination=words
        --spring.cloud.stream.bindings.output.destination=uppercaseWords
        --function.name=uppercase
        --function.code="f -> f.map(s -> s.toString().toUpperCase())"
```

Example REST Application:

```
> java -jar spring-cloud-function-web-1.0.0.BUILD-SNAPSHOT.jar
        --web.path=/demo
        --function.name=uppercase
        --function.code="f -> f.map(s -> s.toString().toUpperCase())"
```

(more docs soon)
