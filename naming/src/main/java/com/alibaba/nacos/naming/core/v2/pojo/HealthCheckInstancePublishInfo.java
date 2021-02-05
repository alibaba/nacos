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

import com.alibaba.nacos.naming.healthcheck.HealthCheckStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Instance publish info with health check for v1.x.
 *
 * @author xiweng.yy
 */
public class HealthCheckInstancePublishInfo extends InstancePublishInfo {
    
    private static final long serialVersionUID = 5424801693490263492L;
    
    private long lastHeartBeatTime = System.currentTimeMillis();
    
    private HealthCheckStatus healthCheckStatus;
    
    public HealthCheckInstancePublishInfo() {
    }
    
    public HealthCheckInstancePublishInfo(String ip, int port) {
        super(ip, port);
    }
    
    public long getLastHeartBeatTime() {
        return lastHeartBeatTime;
    }
    
    public void setLastHeartBeatTime(long lastHeartBeatTime) {
        this.lastHeartBeatTime = lastHeartBeatTime;
    }
    
    public void initHealthCheck() {
        healthCheckStatus = new HealthCheckStatus();
    }
    
    public boolean tryStartCheck() {
        return healthCheckStatus.isBeingChecked.compareAndSet(false, true);
    }
    
    public void finishCheck() {
        healthCheckStatus.isBeingChecked.set(false);
    }
    
    public void resetOkCount() {
        healthCheckStatus.checkOkCount.set(0);
    }
    
    public void resetFailCount() {
        healthCheckStatus.checkFailCount.set(0);
    }
    
    public void setCheckRt(long checkRt) {
        healthCheckStatus.checkRt = checkRt;
    }
    
    @JsonIgnore
    public AtomicInteger getOkCount() {
        return healthCheckStatus.checkOkCount;
    }
    
    @JsonIgnore
    public AtomicInteger getFailCount() {
        return healthCheckStatus.checkFailCount;
    }
}
