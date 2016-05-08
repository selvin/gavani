#!/bin/bash

screen -S "gavani-writer" \
  -d -m \
  gavani-datastore-redis/bin/gavani-datastore-redis \
    -writer \
    -server \
    -admin.port=:9473 \

# fin
