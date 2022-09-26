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
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.plugin.address.common.AddressProperties;
import com.alibaba.nacos.plugin.address.exception.AddressException;

import java.util.Properties;

public class ServerListManager extends AbstractServerListManager implements ServerListFactory, Closeable {
    
    private final String namespace;
    
    private static final String PROPERTY_ADDRESS_PLUGIN = "PropertyAddressPlugin";
    
    public ServerListManager(Properties properties) throws NacosException {
        this(properties, null);
    }
    
    public ServerListManager(Properties properties, String namespace) throws NacosException {
        super(properties);
        this.namespace = namespace;
        if (namespace != null) {
            AddressProperties.setProperties("namespace", namespace);
        }
    }
    
    public boolean isDomain() {
        return this.addressPlugin.getPluginName().equals(PROPERTY_ADDRESS_PLUGIN) && this.getServerList().size() == 1;
    }
    
    public String getNacosDomain() {
        if (isDomain()) {
            return this.addressPlugin.getServerList().get(0);
        }
        return null;
    }
    
    @Override
    protected void initAddressPluginListener() throws AddressException {
        addressPlugin.registerListener(serverLists -> {
            NotifyCenter.publishEvent(new ServerListChangedEvent());
        });
    }
}
