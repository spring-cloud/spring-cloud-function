#!/bin/bash

while getopts ":n:f:" opt; do
  case $opt in
    n)
      NAME=$OPTARG
      ;;
    f)
      FUNC=$OPTARG
      ;;
    esac
done

java -jar ../spring-cloud-function-core/target/spring-cloud-function-core-1.0.0.BUILD-SNAPSHOT-registrar.jar consumer\
 $NAME\
 $FUNC
