/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.address.manager;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Config Server List Manager.
 *
 * @author misakacoder
 */
public class ConfigServerListManager extends AbstractServerListManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigServerListManager.class);
    
    private String name;
    
    private String tenant;
    
    private String namespace;
    
    private String contentPath;
    
    private volatile String currentServer;
    
    private volatile Iterator<String> iterator;
    
    public ConfigServerListManager(NacosClientProperties properties) throws NacosException {
        initNamespace(properties);
        initContextPath(properties);
        initServerList(properties, namespace);
        initName(properties);
    }
    
    @Override
    public ModuleType getModuleType() {
        return ModuleType.CONFIG;
    }
    
    public String getName() {
        return name;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getContentPath() {
        return contentPath;
    }
    
    public boolean isFixedServer() {
        return serverListProvider != null && !serverListProvider.supportRefresh();
    }
    
    public String getAddressServerUrl() {
        return serverListProvider != null ? serverListProvider.getAddressServerUrl() : "";
    }
    
    @Override
    public void afterUpdateServerList() {
        iterator = iterator();
        currentServer = iterator.next();
    }
    
    @Override
    public String getNextServer() {
        if (iterator == null || !iterator.hasNext()) {
            refreshCurrentServer();
            return currentServer;
        }
        try {
            return iterator.next();
        } catch (Exception ignored) {
        
        }
        refreshCurrentServer();
        return currentServer;
    }
    
    @Override
    public String getCurrentServer() {
        if (StringUtils.isBlank(currentServer)) {
            iterator = iterator();
            currentServer = iterator.next();
        }
        return currentServer;
    }
    
    public void updateCurrentServer(String currentServer) {
        this.currentServer = currentServer;
    }
    
    public void refreshCurrentServer() {
        iterator = iterator();
        currentServer = iterator.next();
    }
    
    public Iterator<String> getIterator() {
        return iterator;
    }
    
    private void initNamespace(NacosClientProperties properties) {
        String namespace = TemplateUtils.stringBlankAndThenExecute(properties.getProperty(PropertyKeyConst.NAMESPACE),
                () -> "");
        this.tenant = namespace;
        this.namespace = namespace;
    }
    
    private void initContextPath(NacosClientProperties properties) {
        this.contentPath = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.CONTEXT_PATH), ParamUtil::getDefaultContextPath);
    }
    
    private void initName(NacosClientProperties properties) {
        this.name = TemplateUtils.stringBlankAndThenExecute(properties.getProperty(PropertyKeyConst.SERVER_NAME),
                () -> serverListProvider != null ? serverListProvider.getName() : "");
    }
    
    private Iterator<String> iterator() {
        List<String> serverList = getServerList();
        if (serverList.isEmpty()) {
            LOGGER.error("no server address defined!");
        }
        return new ServerAddressIterator(serverList);
    }
    
    /**
     * Sort the address list, with the same room priority.
     */
    private static class ServerAddressIterator implements Iterator<String> {
        
        static class RandomizedServerAddress implements Comparable<RandomizedServerAddress> {
            
            static Random random = new Random();
            
            String serverIp;
            
            int priority = 0;
            
            int seed;
            
            public RandomizedServerAddress(String ip) {
                try {
                    this.serverIp = ip;
                    /*
                     change random scope from 32 to Integer.MAX_VALUE to fix load balance issue
                     */
                    this.seed = random.nextInt(Integer.MAX_VALUE);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            @Override
            public int compareTo(RandomizedServerAddress other) {
                if (this.priority != other.priority) {
                    return other.priority - this.priority;
                } else {
                    return other.seed - this.seed;
                }
            }
        }
        
        public ServerAddressIterator(List<String> source) {
            sorted = new ArrayList<>();
            for (String address : source) {
                sorted.add(new RandomizedServerAddress(address));
            }
            Collections.sort(sorted);
            iter = sorted.iterator();
        }
        
        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }
        
        @Override
        public String next() {
            return iter.next().serverIp;
        }
        
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
        
        final List<RandomizedServerAddress> sorted;
        
        final Iterator<RandomizedServerAddress> iter;
    }
}
