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

package com.alibaba.nacos.naming.core.v2.pojo;

import java.util.HashMap;
import java.util.Map;

/**
 * Instance POJO of client published for Nacos v2.
 *
 * @author xiweng.yy
 */
public class InstancePublishInfo {
    
    private final String ip;
    
    private final int port;
    
    private Map<String, Object> extendDatum;
    
    private boolean healthy;
    
    public InstancePublishInfo(String ip, int port) {
        this.ip = ip;
        this.port = port;
        extendDatum = new HashMap<>(1);
    }
    
    public String getIp() {
        return ip;
    }
    
    public int getPort() {
        return port;
    }
    
    public Map<String, Object> getExtendDatum() {
        return extendDatum;
    }
    
    public void setExtendDatum(Map<String, Object> extendDatum) {
        this.extendDatum = extendDatum;
    }
    
    public boolean isHealthy() {
        return healthy;
    }
    
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
}
