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

package com.alibaba.nacos.client.naming;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.naming.core.ServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import com.alibaba.nacos.client.naming.utils.InitUtils;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.ValidatorUtils;

import java.util.Map;
import java.util.Properties;

/**
 * Nacos naming maintain service.
 *
 * @author liaochuntao
 * @since 1.0.1
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosNamingMaintainService implements NamingMaintainService {
    
    private String namespace;
    
    private NamingHttpClientProxy serverProxy;
    
    public NacosNamingMaintainService(String serverList) throws NacosException {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverList);
        init(properties);
    }
    
    public NacosNamingMaintainService(Properties properties) throws NacosException {
        init(properties);
    }
    
    private void init(Properties properties) throws NacosException {
        ValidatorUtils.checkInitParam(properties);
        namespace = InitUtils.initNamespaceForNaming(properties);
        InitUtils.initSerialization();
        InitUtils.initWebRootContext(properties);
        ServerListManager serverListManager = new ServerListManager(properties, namespace);
        SecurityProxy securityProxy = new SecurityProxy(properties,
                NamingHttpClientManager.getInstance().getNacosRestTemplate());
        serverProxy = new NamingHttpClientProxy(namespace, securityProxy, serverListManager, properties, null);
    }
    
    @Override
    public void updateInstance(String serviceName, Instance instance) throws NacosException {
        updateInstance(serviceName, Constants.DEFAULT_GROUP, instance);
    }
    
    @Override
    public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
        serverProxy.updateInstance(serviceName, groupName, instance);
    }
    
    @Override
    public Service queryService(String serviceName) throws NacosException {
        return queryService(serviceName, Constants.DEFAULT_GROUP);
    }
    
    @Override
    public Service queryService(String serviceName, String groupName) throws NacosException {
        return serverProxy.queryService(serviceName, groupName);
    }
    
    @Override
    public void createService(String serviceName) throws NacosException {
        createService(serviceName, Constants.DEFAULT_GROUP);
    }
    
    @Override
    public void createService(String serviceName, String groupName) throws NacosException {
        createService(serviceName, groupName, Constants.DEFAULT_PROTECT_THRESHOLD);
    }
    
    @Override
    public void createService(String serviceName, String groupName, float protectThreshold) throws NacosException {
        Service service = new Service();
        service.setName(serviceName);
        service.setGroupName(groupName);
        service.setProtectThreshold(protectThreshold);
        
        createService(service, new NoneSelector());
    }
    
    @Override
    public void createService(String serviceName, String groupName, float protectThreshold, String expression)
            throws NacosException {
        Service service = new Service();
        service.setName(serviceName);
        service.setGroupName(groupName);
        service.setProtectThreshold(protectThreshold);
        
        ExpressionSelector selector = new ExpressionSelector();
        selector.setExpression(expression);
        
        createService(service, selector);
    }
    
    @Override
    public void createService(Service service, AbstractSelector selector) throws NacosException {
        serverProxy.createService(service, selector);
    }
    
    @Override
    public boolean deleteService(String serviceName) throws NacosException {
        return deleteService(serviceName, Constants.DEFAULT_GROUP);
    }
    
    @Override
    public boolean deleteService(String serviceName, String groupName) throws NacosException {
        return serverProxy.deleteService(serviceName, groupName);
    }
    
    @Override
    public void updateService(String serviceName, String groupName, float protectThreshold) throws NacosException {
        Service service = new Service();
        service.setName(serviceName);
        service.setGroupName(groupName);
        service.setProtectThreshold(protectThreshold);
        
        updateService(service, new NoneSelector());
    }
    
    @Override
    public void updateService(String serviceName, String groupName, float protectThreshold,
            Map<String, String> metadata) throws NacosException {
        Service service = new Service();
        service.setName(serviceName);
        service.setGroupName(groupName);
        service.setProtectThreshold(protectThreshold);
        service.setMetadata(metadata);
        
        updateService(service, new NoneSelector());
    }
    
    @Override
    public void updateService(Service service, AbstractSelector selector) throws NacosException {
        serverProxy.updateService(service, selector);
    }
    
    @Override
    public void shutDown() throws NacosException {
        serverProxy.shutdown();
    }
}
