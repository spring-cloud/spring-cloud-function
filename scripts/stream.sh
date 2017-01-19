#!/bin/bash

while getopts ":i:f:o:" opt; do
  case $opt in
    i)
      IN=$OPTARG
      ;;
    f)
      FUNC=$OPTARG
      TYPE=function
      ;;
    o)
      OUT=$OPTARG
      ;;
    esac
done

java -jar ../spring-cloud-function-samples/spring-cloud-function-sample-bytecode/target/function-sample-bytecode-1.0.0.BUILD-SNAPSHOT.jar\
 --management.security.enabled=false\
 --server.port=9999\
 --spring.cloud.stream.bindings.input.destination=$IN\
 --spring.cloud.stream.bindings.output.destination=$OUT\
 --function.name=$TYPE\
 --function.type=$TYPE\
 --function.resource=file:///tmp/function-registry/$TYPE's'/$FUNC.fun
