#!/usr/bin/env bash

curl -s localhost:8000/add -d '{"name":"world"}' -H "Content-Type: text/plain"
echo
echo Waiting...
sleep 1
RESPONSE=`curl -s localhost:8000/take`
echo Got response: $RESPONSE
if [[ "$RESPONSE" == '{"name":"hi world!"}' ]]; then
  exit 0
else
  exit 1
fi
