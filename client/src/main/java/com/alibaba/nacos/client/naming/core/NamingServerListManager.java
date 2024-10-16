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
import com.alibaba.nacos.client.address.PropertiesListProvider;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.slf4j.Logger;

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
    
    private static final Logger LOGGER = LogUtils.logger(NamingServerListManager.class);
    
    private final AtomicInteger currentIndex = new AtomicInteger();
    
    private String nacosDomain;
    
    private boolean isDomain;
    
    @JustForTest
    public NamingServerListManager(Properties properties) {
        this(NacosClientProperties.PROTOTYPE.derive(properties), "");
    }
    
    public NamingServerListManager(NacosClientProperties properties, String namespace) {
        super(properties, namespace);
    }
    
    @Override
    public void start() throws NacosException {
        super.start();
        List<String> serverList = getServerList();
        if (serverList.isEmpty()) {
            throw new NacosLoadException("serverList is empty,please check configuration");
        } else {
            currentIndex.set(new Random().nextInt(serverList.size()));
        }
        if (serverListProvider instanceof PropertiesListProvider) {
            if (serverList.size() == 1) {
                isDomain = true;
                nacosDomain = serverList.get(0);
            }
        }
    }
    
    public String getNacosDomain() {
        return nacosDomain;
    }
    
    public boolean isDomain() {
        return isDomain;
    }
    
    @Override
    protected String getModuleName() {
        return "Naming";
    }
    
    @Override
    protected NacosRestTemplate getNacosRestTemplate() {
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
