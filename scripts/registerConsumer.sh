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

curl -X POST -H "Content-Type: text/plain" -d $FUNC :8080/consumer/$NAME
