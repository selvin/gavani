#!/bin/bash

screen -S "gavani-reader" \
  -d -m \
  gavani-datastore-redis/bin/gavani-datastore-redis \
    -reader \
    -server \
    -admin.port=:9472 \

# fin
