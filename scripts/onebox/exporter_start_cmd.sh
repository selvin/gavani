#!/bin/bash

screen -S "gavani-exporter-companion" \
  -d -m \
  gavani-exporter-companion/bin/gavani-exporter-companion \
    -admin.port=:9471 \
    -statCollectionConfig=../../cfg/exporter.json \

# fin
