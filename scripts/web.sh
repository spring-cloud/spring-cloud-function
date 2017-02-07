#!/bin/bash

while getopts ":s:f:c:p:" opt; do
  case $opt in
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
    p)
      PORT=$OPTARG
      ;;
    esac
done

java -jar ../spring-cloud-function-samples/spring-cloud-function-sample-compiler/target/function-sample-compiler-1.0.0.BUILD-SNAPSHOT.jar\
 --spring.cloud.function.proxy.$FUNC.type=$TYPE\
 --spring.cloud.function.proxy.$FUNC.resource=file:///tmp/function-registry/$TYPE's'/$FUNC.fun\
 --management.security.enabled=false\
 --server.port=$PORT
