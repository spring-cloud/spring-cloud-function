#!/bin/bash

while getopts ":i:s:f:c:o:p:" opt; do
  case $opt in
    i)
      IN=--spring.cloud.stream.bindings.input.destination=$OPTARG
      ;;
    s)
      FUNC=$OPTARG
      TYPE=supplier
      ;;
    f)
      FUNC=$OPTARG
      TYPE=function
      ;;
    c)
      FUNC=$OPTARG
      TYPE=consumer
      ;;
    o)
      OUT=--spring.cloud.stream.bindings.output.destination=$OPTARG
      ;;
    p)
      PORT=$OPTARG
      ;;
    esac
done

java -jar ../spring-cloud-function-samples/spring-cloud-function-sample-compiler/target/function-sample-compiler-1.0.0.BUILD-SNAPSHOT.jar\
 --management.security.enabled=false\
 --server.port=$PORT\
 $IN\
 $OUT\
 --function.name=$FUNC\
 --spring.cloud.function.proxy.$FUNC.type=$TYPE\
 --spring.cloud.function.proxy.$FUNC.resource=file:///tmp/function-registry/$TYPE's'/$FUNC.fun
