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
import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.maintainer.client.remote.HttpClientManager;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server list Manager.
 *
 * @author Nacos
 */
public class DefaultServerListManager extends AbstractServerListManager {
    
    private final AtomicInteger currentIndex = new AtomicInteger();
    
    public DefaultServerListManager(NacosClientProperties properties) {
        super(properties);
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
    }
    
    @Override
    protected String getModuleName() {
        return "Naming";
    }
    
    @Override
    protected NacosRestTemplate getNacosRestTemplate() {
        return HttpClientManager.getInstance().getNacosRestTemplate();
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