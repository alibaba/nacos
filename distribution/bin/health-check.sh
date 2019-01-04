#!/bin/bash

# Copyright 1999-2018 Alibaba Group Holding Ltd.
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at

#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

readonly CURL_BIN=/usr/bin/curl
readonly DEFAULT_PORT=8848
readonly HEALTH_CHECK_API="http://127.0.0.1:$DEFAULT_PORT/nacos/v1/console/health/readiness"

#####################################

get_port() {
  local sys_name="$(uname -s)"

  case "${sys_name}" in
    Linux*)  sys_name=Linux;;
    Darwin*)  sys_name=Mac
  esac

  local pid=`ps ax | grep -i 'nacos-server' | grep -v grep | tail -n 1 | awk '{print $1}'`

  if [[ ${pid} == "" ]]; then
    return
  fi

  if [[ "$sys_name" == "Mac" ]]; then

    local port=`/usr/sbin/lsof -nP -iTCP -sTCP:LISTEN | grep 'java' | grep ${pid} | awk '{print $9}' | awk -F ':' '{print $2}'`

    echo ${port}

  elif [[ "$sys_name" == "Linux" ]]; then

    local port=`/bin/netstat -ltnp | grep ${pid} | awk '{print $4}' | awk -F ':' '{print $2}'`

    echo ${port}

  fi
}

get_health_check_api() {
  local health_check_api=${HEALTH_CHECK_API};

  local port=$( get_port )

  if [[ ${port} != "" ]]; then
    health_check_api=$(echo "${HEALTH_CHECK_API}" | sed "s/:$DEFAULT_PORT/:$port/")
  fi

  echo ${health_check_api}
}

check() {
  local up_message=$1

  local curl_result=`${CURL_BIN} -m 150 "$( get_health_check_api )" "${@}" 2>/dev/null`

  local is_ok="false"

  if [[ "$up_message" != "" ]]; then

    local check_up_result=`echo "$curl_result" | fgrep "$up_message"`

    if [[ "$check_up_result" != "" ]]; then
        is_ok="true"
    else
        is_ok="false"
    fi

  fi

  echo ${is_ok}
}

#####################################
if [[ -x "$CURL_BIN" ]]; then

  echo -n "Nacos Health Check"

  check_result="false"

  for i in $(seq 30) ; do

    echo -n "."

    sleep 1

    check_result=$( check "OK" )

    if [[ "$check_result" == "true" ]]; then
      echo "[  OK  ]"
      break
    fi

  done

  if [[ "$check_result" == "false" ]]; then
    echo "[FAILED]"
  fi

fi
