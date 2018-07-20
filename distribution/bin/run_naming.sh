#!/usr/bin/env bash

mkdir -p ~/conf

PID=`ps -ef | grep naming | grep -v grep | awk '{print $2}'`

kill -9 $PID

rm -f nacos-naming-0.1.0.jar

wget http://gns-upload.cn-hangzhou.oss-cdn.aliyun-inc.com/nacos-naming-0.1.0.jar

/opt/taobao/java/bin/java  -Dcom.alibaba.nacos.naming.server.port=7001 -jar nacos-naming-0.1.0.jar


kill -9 `ps -ef | grep naming | grep -v grep | awk '{print $2}'`;rm -f nacos-naming-0.1.0.jar;wget http://gns-upload.cn-hangzhou.oss-cdn.aliyun-inc.com/nacos-naming-0.1.0.jar;/opt/taobao/java/bin/java  -Dcom.alibaba.nacos.naming.server.port=7001 -jar nacos-naming-0.1.0.jar