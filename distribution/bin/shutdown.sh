#!/bin/sh

pid=`ps ax | grep -i 'nacos' |grep java | grep -v grep | awk '{print $1}'`
if [ -z "$pid" ] ; then
        echo "No nacosServer running."
        exit -1;
fi

echo "The nacosServer(${pid}) is running..."

kill ${pid}

echo "Send shutdown request to nacosServer(${pid}) OK"