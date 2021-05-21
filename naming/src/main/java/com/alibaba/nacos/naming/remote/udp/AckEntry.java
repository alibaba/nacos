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

import org.apache.commons.lang3.StringUtils;

import java.net.DatagramPacket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UDP Ack entity.
 *
 * @author xiweng.yy
 */
public class AckEntry {
    
    public AckEntry(String key, DatagramPacket packet) {
        this.key = key;
        this.origin = packet;
    }
    
    private String key;
    
    private DatagramPacket origin;
    
    private AtomicInteger retryTimes = new AtomicInteger(0);
    
    private Map<String, Object> data;
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setOrigin(DatagramPacket origin) {
        this.origin = origin;
    }
    
    public DatagramPacket getOrigin() {
        return origin;
    }
    
    public void increaseRetryTime() {
        retryTimes.incrementAndGet();
    }
    
    public int getRetryTimes() {
        return retryTimes.get();
    }
    
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public static String getAckKey(String host, int port, long lastRefTime) {
        return StringUtils.strip(host) + "," + port + "," + lastRefTime;
    }
}
