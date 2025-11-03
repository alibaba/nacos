/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.config;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.filter.IConfigFilter;
import com.alibaba.nacos.api.config.listener.FuzzyWatchEventWatcher;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock Naming Service for test {@link com.alibaba.nacos.api.config.ConfigFactory}.
 *
 * @author xiweng.yy
 */
public class NacosConfigService implements ConfigService {
    
    public static final AtomicBoolean IS_THROW_EXCEPTION = new AtomicBoolean(false);
    
    public NacosConfigService(Properties properties) throws NacosException {
        if (IS_THROW_EXCEPTION.get()) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "mock exception");
        }
    }
    
    @Override
    public String getConfig(String dataId, String group, long timeoutMs) throws NacosException {
        return "";
    }
    
    @Override
    public String getConfigAndSignListener(String dataId, String group, long timeoutMs, Listener listener)
            throws NacosException {
        return "";
    }
    
    @Override
    public void addListener(String dataId, String group, Listener listener) throws NacosException {
    
    }
    
    @Override
    public boolean publishConfig(String dataId, String group, String content) throws NacosException {
        return false;
    }
    
    @Override
    public boolean publishConfig(String dataId, String group, String content, String type) throws NacosException {
        return false;
    }
    
    @Override
    public boolean publishConfigCas(String dataId, String group, String content, String casMd5) throws NacosException {
        return false;
    }
    
    @Override
    public boolean publishConfigCas(String dataId, String group, String content, String casMd5, String type)
            throws NacosException {
        return false;
    }
    
    @Override
    public boolean removeConfig(String dataId, String group) throws NacosException {
        return false;
    }
    
    @Override
    public void removeListener(String dataId, String group, Listener listener) {
    
    }
    
    @Override
    public String getServerStatus() {
        return "";
    }
    
    @Override
    public void addConfigFilter(IConfigFilter configFilter) {
    
    }
    
    @Override
    public void shutDown() throws NacosException {
    
    }
    
    @Override
    public void fuzzyWatch(String groupNamePattern, FuzzyWatchEventWatcher watcher) throws NacosException {
    
    }
    
    @Override
    public void fuzzyWatch(String dataIdPattern, String groupNamePattern, FuzzyWatchEventWatcher watcher)
            throws NacosException {
        
    }
    
    @Override
    public Future<Set<String>> fuzzyWatchWithGroupKeys(String groupNamePattern, FuzzyWatchEventWatcher watcher)
            throws NacosException {
        return null;
    }
    
    @Override
    public Future<Set<String>> fuzzyWatchWithGroupKeys(String dataIdPattern, String groupNamePattern,
            FuzzyWatchEventWatcher watcher) throws NacosException {
        return null;
    }
    
    @Override
    public void cancelFuzzyWatch(String groupNamePattern, FuzzyWatchEventWatcher watcher) throws NacosException {
    
    }
    
    @Override
    public void cancelFuzzyWatch(String dataIdPattern, String groupNamePattern, FuzzyWatchEventWatcher watcher)
            throws NacosException {
        
    }
}
