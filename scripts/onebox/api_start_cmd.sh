#!/bin/bash

screen -S "gavani-api" \
  -d -m \
  gavani-api/bin/gavani-api \
    -http.port=:6474
    -admin.port=:9474 \

# fin
