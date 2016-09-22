#!/bin/bash

while getopts ":p:f:" opt; do
  case $opt in
    p)
      WEBPATH=$OPTARG
      ;;
    f)
      FUNC=$OPTARG
      ;;
    esac
done

java -jar ../spring-cloud-function-web/target/spring-cloud-function-web-1.0.0.BUILD-SNAPSHOT.jar\
 --web.path=$WEBPATH\
 --function.name=func\
 --function.code=$FUNC

