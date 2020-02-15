#!/bin/sh

echo "Starting nacos" && \
     cd ~/nacos/bin && \
     ./startup.sh -m standalone && \
     cd ../logs && \
     tail -f start.out
