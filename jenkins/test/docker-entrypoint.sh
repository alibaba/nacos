#!/bin/sh

echo "Starting nacos" && \
     cd /home/nacos/bin && \
     ./startup.sh -m standalone && \
     cd ../logs && \
     tail -f start.out
