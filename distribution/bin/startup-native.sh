#!/bin/bash
# Copyright 1999-2024 Alibaba Group Holding Ltd.
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#===========================================================================================
# Setting system properties
#===========================================================================================

MODE="cluster"
BASE_DIR="/opt/nacos"
CUSTOM_SEARCH_LOCATIONS="file:${BASE_DIR}/conf/"

if [ -f "${BASE_DIR}/target/nacos-server.jar" ]; then
    NACOS_TYPE="Java"
    NACOS_SERVER="${BASE_DIR}/target/nacos-server.jar"
elif [ -x "${BASE_DIR}/target/nacos-server" ]; then
    NACOS_TYPE="Native"
    NACOS_SERVER="${BASE_DIR}/target/nacos-server"
else
    echo "Nacos server application doesn't exist in ${BASE_DIR}/target"
    exit -1
fi

while getopts ":m:" opt; do
    case $opt in
        m)
            MODE=$OPTARG
            ;;
        *)
            echo "Unknown parameter"
            exit 1
            ;;
    esac
done

#===========================================================================================
# Setting start log
#===========================================================================================

if [ ! -f "${BASE_DIR}/logs/start.out" ]; then
    touch "${BASE_DIR}/logs/start.out"
fi

#===========================================================================================
# Setting application properties
#===========================================================================================

RUN_CMD="-Dnacos.home=${BASE_DIR}"
RUN_CMD="${RUN_CMD} -Dnacos.member.list="
RUN_CMD="${RUN_CMD} -Dnacos.preferHostnameOverIp=true"

### If use standalone mode:
if [[ "${MODE}" == "standalone" ]]; then
  RUN_CMD="${RUN_CMD} -Dnacos.standalone=true"
fi

if [[ "${NACOS_TYPE}" == "Java" ]]; then
    ### Define JAVA_HOME, JAVA_PATH and JAVA
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
            JAVA_HOME=$(dirname "$JAVA_PATH")/..
            export JAVA_HOME=$(cd "$JAVA_HOME" && pwd)
        fi
    fi

    export JAVA_HOME
    export JAVA="$JAVA_HOME/bin/java"

    ### Define gc log rule
    JAVA_MAJOR_VERSION=$($JAVA -version 2>&1 | sed -E -n 's/.* version "([0-9]*).*$/\1/p')
    if [[ "$JAVA_MAJOR_VERSION" -ge "9" ]]; then
        RUN_CMD="${RUN_CMD} -Xlog:gc*:file=${BASE_DIR}/logs/nacos_gc.log:time,tags:filecount=10,filesize=102400"
    else
        JAVA_OPT_EXT_FIX="-Djava.ext.dirs=${JAVA_HOME}/jre/lib/ext:${JAVA_HOME}/lib/ext"
        RUN_CMD="${RUN_CMD} -Xloggc:${BASE_DIR}/logs/nacos_gc.log -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=100M"
    fi

    if [[ "${MODE}" == "standalone" ]]; then
        RUN_CMD="${RUN_CMD} -Xms512m -Xmx512m -Xmn256m"
    else
        RUN_CMD="${RUN_CMD} -server -Xms2g -Xmx2g -Xmn1g -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=320m"
    fi

    RUN_CMD="${RUN_CMD} -XX:-OmitStackTraceInFastThrow -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${BASE_DIR}/logs/java_heapdump.hprof"
    RUN_CMD="${RUN_CMD} -XX:-UseLargePages"
    RUN_CMD="${RUN_CMD} -jar ${NACOS_SERVER}"

    RUN_CMD="${RUN_CMD} --server.max-http-header-size=524288"

    if [[ "$JAVA_OPT_EXT_FIX" == "" ]]; then
        RUN_CMD="${JAVA} ${RUN_CMD}"
    else
        RUN_CMD="${JAVA} ${JAVA_OPT_EXT_FIX} ${RUN_CMD}"
    fi
elif [[ "${NACOS_TYPE}" == "Native" ]]; then
    RUN_CMD="${NACOS_SERVER} ${RUN_CMD}"
fi

RUN_CMD="${RUN_CMD} --logging.config=${BASE_DIR}/conf/nacos-logback.xml"
RUN_CMD="${RUN_CMD} --spring.config.additional-location=${CUSTOM_SEARCH_LOCATIONS}"

#===========================================================================================
# Start application
#===========================================================================================

echo "${RUN_CMD} nacos.nacos" > ${BASE_DIR}/logs/start.out 2>&1 &

echo "${NACOS_TYPE} Nacos is now starting with ${MODE} mode, checkout ${BASE_DIR}/logs/start.out for more information."

nohup ${RUN_CMD} nacos.nacos >> ${BASE_DIR}/logs/start.out 2>&1 &