/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.naming.misc.Loggers;

import java.util.Collection;

/**
 * Nacos naming fuzzy watch initial push delay task.
 *
 * @author tanyongquan
 */
public class FuzzyWatchInitDelayTask extends AbstractDelayTask {
    
    private final String taskKey;
    
    private final String clientId;
    
    private final String pattern;
    
    private final Collection<String> matchedService;
    
    private final int originSize;
    
    private final boolean isFinishInit;
    
    public FuzzyWatchInitDelayTask(String taskKey, String clientId, String pattern, Collection<String> matchedService,
            int originSize, long delay, boolean isFinishInit) {
        this.taskKey = taskKey;
        this.clientId = clientId;
        this.pattern = pattern;
        this.matchedService = matchedService;
        this.originSize = originSize;
        this.isFinishInit = isFinishInit;
        setTaskInterval(delay);
        setLastProcessTime(System.currentTimeMillis());
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
        if (!(task instanceof FuzzyWatchInitDelayTask)) {
            return;
        }
        FuzzyWatchInitDelayTask oldTask = (FuzzyWatchInitDelayTask) task;
        if (!isFinishInit) {
            matchedService.addAll(oldTask.getMatchedService());
        }
        setLastProcessTime(Math.min(getLastProcessTime(), task.getLastProcessTime()));
        Loggers.PUSH.info("[FUZZY-WATCH-INIT-PUSH] Task merge for pattern {}", pattern);
    }
    
    public String getTaskKey() {
        return taskKey;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public Collection<String> getMatchedService() {
        return matchedService;
    }
    
    public boolean isFinishInit() {
        return isFinishInit;
    }
    
    public int getOriginSize() {
        return originSize;
    }
    
    public String getClientId() {
        return clientId;
    }
}
