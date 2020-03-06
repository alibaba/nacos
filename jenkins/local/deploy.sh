#!/bin/sh
let_time=$(date "+%Y%m%d")

imageTag="msmp"
projectName="msmp-nacos"
envName="local"

containerName=$(docker ps -a|grep ${projectName}-${envName}|awk '{print $1}')
if [ "$containerName" != "" ] ; then
echo 删除容器 $containerName ...
docker rm -f $containerName
fi
echo 删除镜像
docker rmi -f ${imageTag}/${projectName}:${envName}
echo 运行新的容器 ...

docker build -t ${imageTag}/${projectName}:${envName} .

# 后端
docker run -d --net=host -p 8848:8848 --name ${projectName}-${envName} ${imageTag}/${projectName}:${envName}
