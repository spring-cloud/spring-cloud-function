#!/bin/bash

while getopts ":s:f:c:" opt; do
  case $opt in
    s)
      SUPP=$OPTARG
      ;;
    f)
      FUNC=$OPTARG
      ;;
    c)
      CONS=$OPTARG
      ;;
    esac
done

java -noverify -XX:TieredStopAtLevel=1 -Xss256K -Xms16M -Xmx256M -XX:MaxMetaspaceSize=128M -jar ../spring-cloud-function-task/target/spring-cloud-function-task-2.0.0.BUILD-SNAPSHOT.jar\
 --lambda.supplier=$SUPP --lambda.function=$FUNC --lambda.consumer=$CONS
