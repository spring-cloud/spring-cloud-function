#!/bin/bash

while getopts ":i:f:o:" opt; do
  case $opt in
    i)
      IN=$OPTARG
      ;;
    f)
      FUNC=$OPTARG
      ;;
    o)
      OUT=$OPTARG
      ;;
    esac
done

java -jar ../spring-cloud-function-stream/target/spring-cloud-function-stream-1.0.0.BUILD-SNAPSHOT.jar\
 --spring.cloud.stream.bindings.input.destination=$IN\
 --spring.cloud.stream.bindings.output.destination=$OUT\
 --function.name=$FUNC
