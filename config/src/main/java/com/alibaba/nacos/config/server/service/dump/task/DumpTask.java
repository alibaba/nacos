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

package com.alibaba.nacos.config.server.service.dump.task;

import com.alibaba.nacos.common.task.AbstractDelayTask;

/**
 * Dump data task.
 *
 * @author Nacos
 */
public class DumpTask extends AbstractDelayTask {
    
    public DumpTask(String groupKey, boolean isBeta, boolean isBatch, boolean isTag, String tag, long lastModified,
            String handleIp) {
        this.groupKey = groupKey;
        this.lastModified = lastModified;
        this.handleIp = handleIp;
        this.isBeta = isBeta;
        if (isTag) {
            this.tag = tag;
        } else {
            this.tag = null;
        }
        this.isBatch = isBatch;
        
        //retry interval: 1s
        setTaskInterval(1000L);
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
    }
    
    final String groupKey;
    
    final long lastModified;
    
    final String handleIp;
    
    final boolean isBeta;
    
    final String tag;
    
    final boolean isBatch;
    
    public String getGroupKey() {
        return groupKey;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public String getHandleIp() {
        return handleIp;
    }
    
    public boolean isBeta() {
        return isBeta;
    }
    
    public String getTag() {
        return tag;
    }
    
    public boolean isBatch() {
        return isBatch;
    }
}

