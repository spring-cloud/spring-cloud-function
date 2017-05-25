#!/bin/bash

while getopts ":n:f:t:" opt; do
  case $opt in
    n)
      NAME=$OPTARG
      ;;
    f)
      FUNC=$OPTARG
      ;;
    t)
      TYPE=$OPTARG
      ;;
    esac
done

curl -X POST -H "Content-Type: text/plain" -d $FUNC localhost:8080/supplier/$NAME?type=$TYPE
