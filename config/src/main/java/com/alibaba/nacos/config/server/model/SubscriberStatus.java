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

package com.alibaba.nacos.config.server.model;

import java.io.Serializable;

/**
 * SubscriberStatus.
 *
 * @author Nacos
 */
public class SubscriberStatus implements Serializable {
    
    private static final long serialVersionUID = 1065466896062351086L;
    
    private String groupKey;
    
    private String md5;
    
    private Long lastTime;
    
    private Boolean status;
    
    private String serverIp;
    
    public SubscriberStatus() {
    }
    
    public SubscriberStatus(String groupKey, Boolean status, String md5, Long lastTime) {
        this.groupKey = groupKey;
        this.md5 = md5;
        this.lastTime = lastTime;
        this.status = status;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public void setMd5(String md5) {
        this.md5 = md5;
    }
    
    public Long getLastTime() {
        return lastTime;
    }
    
    public void setLastTime(Long lastTime) {
        this.lastTime = lastTime;
    }
    
    public Boolean getStatus() {
        return status;
    }
    
    public void setStatus(Boolean status) {
        this.status = status;
    }
    
    public String getGroupKey() {
        
        return groupKey;
    }
    
    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }
    
    public String getServerIp() {
        return serverIp;
    }
    
    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }
}
