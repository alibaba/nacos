/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.config.server.service.watch;

import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;

import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class WatchClient {
    
    private String tag;
    
    private final String appName;
    
    private final String address;
    
    /**
     *
     */
    private final String namespace;
    
    /**
     * [groupID#dataID, MD5(configContent)]
     */
    private final Map<String, String> watchKey;
    
    protected WatchClientManager clientManager;
    
    public WatchClient(String appName, String address, String namespace, Map<String, String> watchKey) {
        this.appName = appName;
        this.address = address;
        this.namespace = namespace;
        this.watchKey = watchKey;
    }
    
    /**
     *
     */
    public abstract void init();
    
    public final void injectWatchClientManager(final WatchClientManager clientManager) {
        this.clientManager = clientManager;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public String getTag() {
        return tag;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public String getAddress() {
        return address;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public Map<String, String> getWatchKey() {
        return watchKey;
    }
    
    protected abstract void notifyChangeEvent(LocalDataChangeEvent event);
    
    /**
     * Listening protocol type
     *
     * @return protocol type
     */
    protected abstract String protocol();
    
}
