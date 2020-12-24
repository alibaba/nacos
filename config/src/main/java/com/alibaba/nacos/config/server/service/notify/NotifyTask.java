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

package com.alibaba.nacos.config.server.service.notify;

import com.alibaba.nacos.common.task.AbstractDelayTask;

/**
 * Notify task.
 *
 * @author Nacos
 */
public class NotifyTask extends AbstractDelayTask {
    
    private String dataId;
    
    private String group;
    
    private String tenant;
    
    private long lastModified;
    
    private int failCount;
    
    public NotifyTask(String dataId, String group, String tenant, long lastModified) {
        this.dataId = dataId;
        this.group = group;
        this.setTenant(tenant);
        this.lastModified = lastModified;
        setTaskInterval(3000L);
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public int getFailCount() {
        return failCount;
    }
    
    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
        // Perform merge, but do nothing, tasks with the same dataId and group, later will replace the previous
        
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
}
