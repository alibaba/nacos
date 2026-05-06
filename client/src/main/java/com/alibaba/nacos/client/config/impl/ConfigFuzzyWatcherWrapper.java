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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.config.listener.FuzzyWatchEventWatcher;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ConfigFuzzyWatcherWrapper.
 *
 * @author shiyiyue
 */
public class ConfigFuzzyWatcherWrapper {
    
    long syncVersion = 0;
    
    FuzzyWatchEventWatcher fuzzyWatchEventWatcher;
    
    public ConfigFuzzyWatcherWrapper(FuzzyWatchEventWatcher fuzzyWatchEventWatcher) {
        this.fuzzyWatchEventWatcher = fuzzyWatchEventWatcher;
    }
    
    /**
     * Unique identifier for the listener.
     */
    String uuid = UUID.randomUUID().toString();
    
    private Set<String> syncGroupKeys = new HashSet<>();
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigFuzzyWatcherWrapper that = (ConfigFuzzyWatcherWrapper) o;
        return Objects.equals(fuzzyWatchEventWatcher, that.fuzzyWatchEventWatcher) && Objects.equals(uuid, that.uuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fuzzyWatchEventWatcher, uuid);
    }
    
    Set<String> getSyncGroupKeys() {
        return syncGroupKeys;
    }
    
    /**
     * Get the UUID (Unique Identifier) of the listener.
     *
     * @return The UUID of the listener
     */
    String getUuid() {
        return uuid;
    }
    
}
