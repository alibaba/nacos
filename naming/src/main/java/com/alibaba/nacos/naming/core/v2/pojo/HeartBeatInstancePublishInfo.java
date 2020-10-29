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

/**
 * Instance publish info with heart beat time for v1.x.
 *
 * @author xiweng.yy
 */
public class HeartBeatInstancePublishInfo extends InstancePublishInfo {
    
    private long lastHeartBeatTime = System.currentTimeMillis();
    
    public HeartBeatInstancePublishInfo() {
    }
    
    public HeartBeatInstancePublishInfo(String ip, int port) {
        super(ip, port);
    }
    
    public long getLastHeartBeatTime() {
        return lastHeartBeatTime;
    }
    
    public void setLastHeartBeatTime(long lastHeartBeatTime) {
        this.lastHeartBeatTime = lastHeartBeatTime;
    }
}
