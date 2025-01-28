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

package com.alibaba.nacos.maintainer.client.address;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.env.NacosClientProperties;
import com.alibaba.nacos.maintainer.client.remote.HttpClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Server list Manager.
 *
 * @author Nacos
 */
@Service
public class DefaultServerListManager extends AbstractServerListManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultServerListManager.class);
    
    private volatile String currentServerAddr;
    
    private Iterator<String> iterator;
    
    public DefaultServerListManager(NacosClientProperties properties) {
        super(properties);
    }
    
    @Override
    protected NacosRestTemplate getNacosRestTemplate() {
        return HttpClientManager.getInstance().getNacosRestTemplate();
    }
    
    @Override
    public void start() throws NacosException {
        super.start();
        iterator = iterator();
        currentServerAddr = iterator.next();
    }
    
    Iterator<String> iterator() {
        List<String> serverList = getServerList();
        if (serverList.isEmpty()) {
            LOGGER.error("[iterator-serverlist] No server address defined!");
        }
        return new ServerAddressIterator(serverList);
    }
    
    @Override
    public String genNextServer() {
        if (iterator == null || !iterator.hasNext()) {
            refreshCurrentServerAddr();
            return currentServerAddr;
        }
        try {
            return iterator.next();
        } catch (Exception ignored) {
        }
        refreshCurrentServerAddr();
        return currentServerAddr;
    }
    
    @Override
    public String getCurrentServer() {
        if (StringUtils.isBlank(currentServerAddr)) {
            iterator = iterator();
            currentServerAddr = iterator.next();
        }
        return currentServerAddr;
    }
    
    public String getUrlString() {
        return getServerList().toString();
    }
    
    @Override
    public String toString() {
        return "ServerManager-"  + "-" + getUrlString();
    }
    
    public boolean contain(String ip) {
        return getServerList().contains(ip);
    }
    
    public void refreshCurrentServerAddr() {
        iterator = iterator();
        currentServerAddr = iterator.next();
    }
    
    public void updateCurrentServerAddr(String currentServerAddr) {
        this.currentServerAddr = currentServerAddr;
    }
    
    public Iterator<String> getIterator() {
        return iterator;
    }
    
    /**
     * Sort the address list, with the same room priority.
     */
    private static class ServerAddressIterator implements Iterator<String> {
        
        static class RandomizedServerAddress implements Comparable<RandomizedServerAddress> {
            
            static Random random = new Random();
            
            String serverIp;
            
            int seed;
            
            public RandomizedServerAddress(String ip) {
                this.serverIp = ip;
                /*
                 change random scope from 32 to Integer.MAX_VALUE to fix load balance issue
                 */
                this.seed = random.nextInt(Integer.MAX_VALUE);
            }
            
            @Override
            public int compareTo(RandomizedServerAddress other) {
                return other.seed - this.seed;
            }
        }
        
        final List<RandomizedServerAddress> sorted;
        
        final Iterator<RandomizedServerAddress> iter;
        
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
    }
}