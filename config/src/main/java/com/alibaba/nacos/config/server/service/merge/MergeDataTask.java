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

package com.alibaba.nacos.config.server.service.merge;

import com.alibaba.nacos.common.task.AbstractDelayTask;

/**
 * Represents the task of aggregating data.
 *
 * @author jiuRen
 */
class MergeDataTask extends AbstractDelayTask {
    
    static final long DELAY = 0L;
    
    final String dataId;
    
    final String groupId;
    
    final String tenant;
    
    final String tag;
    
    private final String clientIp;
    
    MergeDataTask(String dataId, String groupId, String tenant, String clientIp) {
        this(dataId, groupId, tenant, null, clientIp);
    }
    
    MergeDataTask(String dataId, String groupId, String tenant, String tag, String clientIp) {
        this.dataId = dataId;
        this.groupId = groupId;
        this.tenant = tenant;
        this.tag = tag;
        this.clientIp = clientIp;
        
        // aggregation delay
        setTaskInterval(DELAY);
        setLastProcessTime(System.currentTimeMillis());
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
    }
    
    public String getId() {
        return toString();
    }
    
    @Override
    public String toString() {
        return "MergeTask[" + dataId + ", " + groupId + ", " + tenant + ", " + clientIp + "]";
    }
    
    public String getClientIp() {
        return clientIp;
    }
}
