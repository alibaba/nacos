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

package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.address.AddressServerListProvider;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;

import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Naming server list manager.
 *
 * @author xiweng.yy
 */
public class NamingServerListManager extends AbstractServerListManager {
    
    private final AtomicInteger currentIndex = new AtomicInteger();
    
    private String nacosDomain;
    
    private boolean isDomain;
    
    public NamingServerListManager(Properties properties) throws NacosException {
        this(NacosClientProperties.PROTOTYPE.derive(properties));
    }
    
    public NamingServerListManager(NacosClientProperties properties) throws NacosException {
        this(properties, "");
    }
    
    public NamingServerListManager(NacosClientProperties properties, String namespace) throws NacosException {
        super(properties, namespace);
        List<String> serverList = getServerList();
        if (serverList.isEmpty()) {
            throw new NacosLoadException("serverList is empty,please check configuration");
        } else {
            currentIndex.set(new Random().nextInt(serverList.size()));
        }
        if (serverListProvider instanceof AddressServerListProvider) {
            if (serverList.size() == 1) {
                isDomain = true;
                nacosDomain = serverList.get(0);
            }
        }
    }
    
    public String getNacosDomain() {
        return nacosDomain;
    }
    
    public void setNacosDomain(final String nacosDomain) {
        this.nacosDomain = nacosDomain;
    }
    
    public boolean isDomain() {
        return isDomain;
    }
    
    public void setDomain(final boolean domain) {
        isDomain = domain;
    }
    
    @Override
    public String getModuleName() {
        return "Naming";
    }
    
    @Override
    public NacosRestTemplate getNacosRestTemplate() {
        return NamingHttpClientManager.getInstance().getNacosRestTemplate();
    }
    
    @Override
    public String genNextServer() {
        int index = currentIndex.incrementAndGet() % getServerList().size();
        return getServerList().get(index);
    }
    
    @Override
    public String getCurrentServer() {
        return getServerList().get(currentIndex.get() % getServerList().size());
    }
}
