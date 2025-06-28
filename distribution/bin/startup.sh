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

cygwin=false
darwin=false
os400=false
case "`uname`" in
CYGWIN*) cygwin=true;;
Darwin*) darwin=true;;
OS400*) os400=true;;
esac
error_exit ()
{
    echo "ERROR: $1 !!"
    exit 1
}

validate_base64() {
    decode_cmd=""
    if command -v base64 &> /dev/null; then
        decode_cmd="base64 -d"
    else
        return 0;
    fi
    local input_str=$1
    decoded_content=$(echo "$input_str" | $decode_cmd)
    decoded_result=$?
    if [ $decoded_result -ne 0 ]; then
        echo "Invalid Base64 string: $input_str, please input again."
        return 1
    fi
    decoded_content_length=$(echo -n "$decoded_content" | wc -c)
    # adapt macOS base64 -d
    if [ ${decoded_content_length} -eq 0 ]; then
        echo "Invalid Base64 string: $input_str, please input again."
        return 1
    fi
    if [ ${decoded_content_length} -lt 32 ]; then
        echo "Invalid original token.secret.key, please use more than 32 length string do Base64 encode, please input again."
        return 1
    fi
    return 0
}

process_required_config() {
    local key_pattern="$1"
    local target_file="$2"
    local isBase64="${3:-false}"
    local escaped_key=$(echo "$key_pattern" | sed 's/\./\\./g')

    if grep -q "^${escaped_key}=$" "${target_file}"; then
        hint_message="\`${key_pattern}\` is missing, please set: "
        if [ "$isBase64" = "true" ]; then
            hint_message="\`${key_pattern}\` is missing, please set with Base64 string: "
            echo "The initial key used to generate JWT tokens (the original string must be over 32 characters and Base64 encoded)."
            echo "用于密码生成JWT Token的初始密钥（原串长度32位以上做Base64格式化）。"
        fi
        read -p "${hint_message}" input_val
        inputCheckPass=1
        if [ "$isBase64" = "true" ]; then
            while [ $inputCheckPass -ne 0 ]; do
                validate_base64 "${input_val}"
                inputCheckPass=$?
                if [ $inputCheckPass -ne 0 ]; then
                  read -p "${hint_message}" input_val
                fi
            done
        fi

        if sed -i.bak "s/^\(${escaped_key}=\)$/\1${input_val}/" "${target_file}" 2>/dev/null; then
            rm -f "${target_file}.bak"
            echo "\`${key_pattern}\` Updated: "
            grep "^${escaped_key}" "$2" | head -n1
            echo "----------------------------------"
        else
            # MacOS系统处理
            sed -i "" "s/^\(${escaped_key}=\)$/\1${input_val}/" "${target_file}"
            echo "\`${key_pattern}\` Updated: "
            grep "^${escaped_key}" "$2" | head -n1
            echo "----------------------------------"
        fi
    fi
}

[ ! -e "$JAVA_HOME/bin/java" ] && JAVA_HOME=$HOME/jdk/java
[ ! -e "$JAVA_HOME/bin/java" ] && JAVA_HOME=/usr/java
[ ! -e "$JAVA_HOME/bin/java" ] && JAVA_HOME=/opt/taobao/java
[ ! -e "$JAVA_HOME/bin/java" ] && unset JAVA_HOME

if [ -z "$JAVA_HOME" ]; then
  if $darwin; then

    if [ -x '/usr/libexec/java_home' ] ; then
      export JAVA_HOME=`/usr/libexec/java_home`

    elif [ -d "/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home" ]; then
      export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home"
    fi
  else
    JAVA_PATH=`dirname $(readlink -f $(which javac))`
    if [ "x$JAVA_PATH" != "x" ]; then
      export JAVA_HOME=`dirname $JAVA_PATH 2>/dev/null`
    fi
  fi
  if [ -z "$JAVA_HOME" ]; then
        JAVA_PATH=$(which java)
        if [ -z "$JAVA_PATH" ]; then
            error_exit "Please set the JAVA_HOME variable in your environment, We need java(x64)! jdk8 or later is better!"
        fi
        if [ -L "$JAVA_PATH" ]; then
            JAVA_PATH=$(readlink -f "$JAVA_PATH")
        fi
        JAVA_HOME=$(dirname "$JAVA_PATH")/..
        export JAVA_HOME=$(cd "$JAVA_HOME" && pwd)
  fi
fi

export SERVER="nacos-server"
export MODE="cluster"
export FUNCTION_MODE="all"
export MEMBER_LIST=""
export EMBEDDED_STORAGE=""
export DEPLOYMENT="merged"
while getopts ":m:f:s:c:p:d:" opt
do
    case $opt in
        m)
            MODE=$OPTARG;;
        f)
            FUNCTION_MODE=$OPTARG;;
        s)
            SERVER=$OPTARG;;
        c)
            MEMBER_LIST=$OPTARG;;
        p)
            EMBEDDED_STORAGE=$OPTARG;;
        d)
            DEPLOYMENT=$OPTARG;;
        ?)
        echo "Unknown parameter"
        exit 1;;
    esac
done

export JAVA_HOME
export JAVA="$JAVA_HOME/bin/java"
export BASE_DIR=`cd $(dirname $0)/..; pwd`
export CUSTOM_SEARCH_LOCATIONS=file:${BASE_DIR}/conf/

#===========================================================================================
# Check and Init properties
#===========================================================================================
process_required_config "nacos.core.auth.plugin.nacos.token.secret.key" ${BASE_DIR}/conf/application.properties true
process_required_config "nacos.core.auth.server.identity.key" ${BASE_DIR}/conf/application.properties
process_required_config "nacos.core.auth.server.identity.value" ${BASE_DIR}/conf/application.properties

#===========================================================================================
# JVM Configuration
#===========================================================================================
if [[ "${MODE}" == "standalone" ]]; then
    JAVA_OPT="${JAVA_OPT} ${CUSTOM_NACOS_MEMORY:- -Xms512m -Xmx512m -Xmn256m}"
    JAVA_OPT="${JAVA_OPT} -Dnacos.standalone=true"
else
    if [[ "${EMBEDDED_STORAGE}" == "embedded" ]]; then
        JAVA_OPT="${JAVA_OPT} -DembeddedStorage=true"
    fi
    JAVA_OPT="${JAVA_OPT} -server ${CUSTOM_NACOS_MEMORY:- -Xms2g -Xmx2g -Xmn1g -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=320m}"
    JAVA_OPT="${JAVA_OPT} -XX:-OmitStackTraceInFastThrow -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${BASE_DIR}/logs/java_heapdump.hprof"
    JAVA_OPT="${JAVA_OPT} -XX:-UseLargePages"
fi

if [[ "${FUNCTION_MODE}" == "config" ]]; then
    JAVA_OPT="${JAVA_OPT} -Dnacos.functionMode=config"
elif [[ "${FUNCTION_MODE}" == "naming" ]]; then
    JAVA_OPT="${JAVA_OPT} -Dnacos.functionMode=naming"
fi

JAVA_OPT="${JAVA_OPT} -Dnacos.member.list=${MEMBER_LIST}"

JAVA_MAJOR_VERSION=$($JAVA -version 2>&1 | sed -E -n 's/.* version "([0-9]*).*$/\1/p')
if [[ "$JAVA_MAJOR_VERSION" -ge "9" ]] ; then
  JAVA_OPT="${JAVA_OPT} -Xlog:gc*:file=${BASE_DIR}/logs/nacos_gc.log:time,tags:filecount=10,filesize=100m"
else
  JAVA_OPT="${JAVA_OPT} -XX:+UseConcMarkSweepGC -XX:+UseCMSCompactAtFullCollection -XX:CMSInitiatingOccupancyFraction=70 -XX:+CMSParallelRemarkEnabled -XX:SoftRefLRUPolicyMSPerMB=0 -XX:+CMSClassUnloadingEnabled -XX:SurvivorRatio=8 "
  JAVA_OPT_EXT_FIX="-Djava.ext.dirs=${JAVA_HOME}/jre/lib/ext:${JAVA_HOME}/lib/ext"
  JAVA_OPT="${JAVA_OPT} -Xloggc:${BASE_DIR}/logs/nacos_gc.log -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=100M"
fi

JAVA_OPT="${JAVA_OPT} -Dnacos.deployment.type=${DEPLOYMENT}"
JAVA_OPT="${JAVA_OPT} -Dloader.path=${BASE_DIR}/plugins,${BASE_DIR}/plugins/health,${BASE_DIR}/plugins/cmdb,${BASE_DIR}/plugins/selector"
JAVA_OPT="${JAVA_OPT} -Dnacos.home=${BASE_DIR}"
JAVA_OPT="${JAVA_OPT} -jar ${BASE_DIR}/target/${SERVER}.jar"
JAVA_OPT="${JAVA_OPT} ${JAVA_OPT_EXT}"
JAVA_OPT="${JAVA_OPT} --spring.config.additional-location=${CUSTOM_SEARCH_LOCATIONS}"
JAVA_OPT="${JAVA_OPT} --logging.config=${BASE_DIR}/conf/nacos-logback.xml"
JAVA_OPT="${JAVA_OPT} --server.max-http-request-header-size=524288"

if [ ! -d "${BASE_DIR}/logs" ]; then
  mkdir ${BASE_DIR}/logs
fi

echo "$JAVA $JAVA_OPT_EXT_FIX ${JAVA_OPT}"

if [[ "${MODE}" == "standalone" ]]; then
    echo "nacos is starting with standalone"
else
    echo "nacos is starting with cluster"
fi

logfile="${BASE_DIR}/logs/startup.log"
if [ ! -f "$logfile" ]; then
  touch "$logfile"
fi

echo "$JAVA $JAVA_OPT_EXT_FIX ${JAVA_OPT}" > "$logfile"

if [[ "$JAVA_OPT_EXT_FIX" == "" ]]; then
  nohup "$JAVA" ${JAVA_OPT} nacos.nacos >> "$logfile" 2>&1 &
else
  nohup "$JAVA" "$JAVA_OPT_EXT_FIX" ${JAVA_OPT} nacos.nacos >> "$logfile" 2>&1 &
fi

echo "nacos is starting. you can check the ${logfile}"
