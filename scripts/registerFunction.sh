#!/bin/bash

while getopts ":n:f:i:o:" opt; do
  case $opt in
    n)
      NAME=$OPTARG
      ;;
    f)
      FUNC=$OPTARG
      ;;
    i)
      INTYPE=$OPTARG
      ;;
    o)
      OUTTYPE=$OPTARG
      ;;
    esac
done

curl -X POST -H "Content-Type: text/plain" -d $FUNC "localhost:8080/function/$NAME?inputType=$INTYPE&outputType=$OUTTYPE"

