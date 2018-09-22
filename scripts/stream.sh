#!/bin/bash

PREFIX="--spring.cloud.function.import"
DIR="file:///tmp/function-registry"

tokenize() {
  local IFS=,
  local TOKENS=($1)
  echo ${TOKENS[@]}
}

DURATION=0

while getopts ":i:s:f:c:o:p:d:" opt; do
  case $opt in
    i)
      IN=--spring.cloud.stream.bindings.input.destination=$OPTARG
      ;;
    s)
      FUNC=$OPTARG
      TYPE="$PREFIX.$FUNC.type=supplier"
      RESOURCE="$PREFIX.$FUNC.location=$DIR/suppliers/$FUNC.fun"
      ;;
    f)
      FUNC=$OPTARG
      for i in `tokenize $OPTARG`; do
        RESOURCE="$RESOURCE $PREFIX.${i}.location=$DIR/functions/${i}.fun"
        TYPE="$TYPE $PREFIX.${i}.type=function"
      done
      ;;
    c)
      FUNC=$OPTARG
      TYPE="$PREFIX.$FUNC.type=consumer"
      RESOURCE="$PREFIX.$FUNC.location=$DIR/consumers/$FUNC.fun"
      ;;
    o)
      OUT=--spring.cloud.stream.bindings.output.destination=$OPTARG
      ;;
    p)
      PORT=$OPTARG
      ;;
    d)
      DURATION=$OPTARG
      ;;
    esac
done

java -jar ../spring-cloud-function-samples/function-sample-compiler/target/function-sample-compiler-2.0.0.BUILD-SNAPSHOT.jar\
 --management.security.enabled=false\
 --server.port=$PORT\
 --spring.cloud.function.stream.endpoint=$FUNC\
 --spring.cloud.function.stream.interval=$DURATION\
 $IN\
 $OUT\
 $RESOURCE\
 $TYPE
