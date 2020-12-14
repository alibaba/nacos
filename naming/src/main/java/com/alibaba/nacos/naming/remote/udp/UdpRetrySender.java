/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.naming.remote.udp;

import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.AckEntry;

/**
 * UDP sender for retry.
 *
 * @author xiweng.yy
 */
public class UdpRetrySender implements Runnable {
    
    private final AckEntry ackEntry;
    
    private final UdpConnector udpConnector;
    
    public UdpRetrySender(AckEntry ackEntry, UdpConnector udpConnector) {
        this.ackEntry = ackEntry;
        this.udpConnector = udpConnector;
    }
    
    @Override
    public void run() {
        if (udpConnector.containAck(ackEntry.getKey())) {
            Loggers.PUSH.info("retry to push data, key: " + ackEntry.getKey());
            udpConnector.sendDataWithoutCallback(ackEntry);
        }
    }
}
