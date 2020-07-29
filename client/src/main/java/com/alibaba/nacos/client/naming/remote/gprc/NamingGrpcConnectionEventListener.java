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

package com.alibaba.nacos.client.naming.remote.gprc;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.remote.ConnectionEventListener;
import com.alibaba.nacos.client.utils.LogUtils;

import java.util.Map;

/**
 * Naming client gprc connection event listener.
 *
 * @author xiweng.yy
 */
public class NamingGrpcConnectionEventListener implements ConnectionEventListener {
    
    private final ServiceInfoHolder serviceInfoHolder;
    
    private final NamingGrpcClientProxy clientProxy;
    
    public NamingGrpcConnectionEventListener(ServiceInfoHolder serviceInfoHolder, NamingGrpcClientProxy clientProxy) {
        this.serviceInfoHolder = serviceInfoHolder;
        this.clientProxy = clientProxy;
    }
    
    @Override
    public void onConnected() {
    
    }
    
    @Override
    public void onReconnected() {
        for (Map.Entry<String, ServiceInfo> each : serviceInfoHolder.getServiceInfoMap().entrySet()) {
            ServiceInfo serviceInfo = each.getValue();
            String serviceName = NamingUtils.getServiceName(serviceInfo.getName());
            String groupName = NamingUtils.getGroupName(serviceInfo.getName());
            try {
                clientProxy.subscribe(serviceName, groupName, serviceInfo.getClusters());
            } catch (NacosException e) {
                LogUtils.NAMING_LOGGER.warn(String.format("re subscribe service %s failed", serviceInfo.getName()), e);
            }
        }
    }
    
    @Override
    public void onDisConnect() {
    
    }
}
