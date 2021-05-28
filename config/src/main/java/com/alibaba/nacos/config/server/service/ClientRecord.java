/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ClientRecord saves records which fetch from client-side.
 *
 * @author zongtanghu
 */
public class ClientRecord {
    
    private final String ip;
    
    private volatile long lastTime;
    
    private final ConcurrentMap<String, String> groupKey2md5Map;
    
    private final ConcurrentMap<String, Long> groupKey2pollingTsMap;
    
    public ClientRecord(final String clientIp) {
        this.ip = clientIp;
        this.groupKey2md5Map = new ConcurrentHashMap<String, String>(20, 0.75f, 1);
        this.groupKey2pollingTsMap = new ConcurrentHashMap<String, Long>(20, 0.75f, 1);
    }
    
    public String getIp() {
        return ip;
    }
    
    public long getLastTime() {
        return lastTime;
    }
    
    public void setLastTime(long lastTime) {
        this.lastTime = lastTime;
    }
    
    public ConcurrentMap<String, String> getGroupKey2md5Map() {
        return groupKey2md5Map;
    }
    
    public ConcurrentMap<String, Long> getGroupKey2pollingTsMap() {
        return groupKey2pollingTsMap;
    }
}
