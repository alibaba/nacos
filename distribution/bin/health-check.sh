#!/bin/bash

readonly CURL_BIN=/usr/bin/curl
readonly HEALTH_CHECK_API="http://127.0.0.1:8848/nacos/v1/console/health/readiness"

#####################################
check() {
  local up_message=$1

  local curl_result=`${CURL_BIN} -m 150 "${HEALTH_CHECK_API}" "${@}" 2>/dev/null`

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
