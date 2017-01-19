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

java -jar ../spring-cloud-function-samples/spring-cloud-function-sample-bytecode/target/function-sample-bytecode-1.0.0.BUILD-SNAPSHOT.jar \
--function.type=$TYPE \
--function.resource=file:///tmp/function-registry/$TYPE's'/$FUNC.fun \
--server.port=$PORT
