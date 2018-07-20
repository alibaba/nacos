#!/usr/bin/env bash

cd ../../naming

mvn clean install -Dmaven.test.skip=true;osscmd upload target/nacos-naming-0.1.0.jar oss://gns-upload/nacos-naming-0.1.0.jar