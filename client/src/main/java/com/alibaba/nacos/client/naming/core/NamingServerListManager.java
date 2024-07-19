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
import com.alibaba.nacos.client.env.NacosClientProperties;

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
    
    public NamingServerListManager(Properties properties) throws NacosException {
        this(NacosClientProperties.PROTOTYPE.derive(properties));
    }
    
    public NamingServerListManager(NacosClientProperties properties) throws NacosException {
        this(properties, "");
    }
    
    public NamingServerListManager(NacosClientProperties properties, String namespace) throws NacosException {
        super(properties, namespace);
        if (getServerList().isEmpty()) {
            throw new NacosLoadException("serverList is empty,please check configuration");
        } else {
            currentIndex.set(new Random().nextInt(getServerList().size()));
        }
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
