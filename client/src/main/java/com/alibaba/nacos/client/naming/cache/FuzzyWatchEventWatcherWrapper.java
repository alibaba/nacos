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

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.naming.listener.FuzzyWatchEventWatcher;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * fuzzy watcher wrapper.
 *
 * @author shiyiyue
 */
public class FuzzyWatchEventWatcherWrapper {
    
    long syncVersion = 0;
    
    FuzzyWatchEventWatcher fuzzyWatchEventWatcher;
    
    String uuid = UUID.randomUUID().toString();
    
    public FuzzyWatchEventWatcherWrapper(FuzzyWatchEventWatcher fuzzyWatchEventWatcher) {
        this.fuzzyWatchEventWatcher = fuzzyWatchEventWatcher;
    }
    
    private Set<String> syncServiceKeys = new HashSet<>();
    
    final String getUuid() {
        return uuid;
    }
    
    Set<String> getSyncServiceKeys() {
        return syncServiceKeys;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FuzzyWatchEventWatcherWrapper that = (FuzzyWatchEventWatcherWrapper) o;
        return Objects.equals(fuzzyWatchEventWatcher, that.fuzzyWatchEventWatcher);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fuzzyWatchEventWatcher);
    }
}
