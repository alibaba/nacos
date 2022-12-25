#
#  Copyright 1999-2018 Alibaba Group Holding Ltd.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
ARG REVISION

FROM openjdk:8-jre-slim-buster

RUN mkdir -p /home/nacos

COPY target/nacos-server-${REVISION} /home/nacos/nacos-config

WORKDIR /home/nacos/nacos-config

# 8848: default web server port
# 8847: default peer listenning port
# 8807: default raft port
EXPOSE 8848 8847 8807

ENTRYPOINT [ "/bin/bash", "/home/nacos/nacos-config/bin/startup-in-container.sh" ]
