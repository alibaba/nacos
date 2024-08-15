#!/bin/bash

# Copyright 1999-2024 Alibaba Group Holding Ltd.
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

export SERVER="nacos-server"
export MODE="cluster"
while getopts ":m:" opt
do
    case $opt in
        m)
            MODE=$OPTARG;;
        ?)
        echo "Unknown parameter"
        exit 1;;
    esac
done

#===========================================================================================
# Configuration
#===========================================================================================
if [[ "${MODE}" == "standalone" ]]; then
    OPT="${OPT} -Dnacos.standalone=true"
fi

OPT="${OPT} -Dnacos.home='/Users/dioxide/Project Cache/Cache/nacos'"

if [[ "${MODE}" == "standalone" ]]; then
    echo "native nacos is starting with standalone"
else
    echo "native nacos is starting with cluster"
fi

BASE_DIR="/Users/dioxide/Project Cache/Project Com/nacos/console/target/nacos-server"

echo "$BASE_DIR" "$OPT"
