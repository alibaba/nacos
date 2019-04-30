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
import com.alibaba.nacos.api.naming.MaintainService;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.NamingUtils;
import com.alibaba.nacos.client.utils.StringUtils;

import java.util.Map;
import java.util.Properties;

/**
 * @author liaochuntao
 * @since 1.0.0
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosMaintainService implements MaintainService {

    private String namespace;

    private String endpoint;

    private String serverList;

    private NamingProxy serverProxy;

    public NacosMaintainService(String serverList) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverList);

        init(properties);
    }

    public NacosMaintainService(Properties properties) {

        init(properties);
    }

    private void init(Properties properties) {
        namespace = NamingUtils.initNamespace(properties);
        initServerAddr(properties);
        NamingUtils.initWebRootContext();

        serverProxy = new NamingProxy(namespace, endpoint, serverList);
        serverProxy.setProperties(properties);
    }

    private void initServerAddr(Properties properties) {
        serverList = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
        endpoint = NamingUtils.initEndpoint(properties);
        if (StringUtils.isNotEmpty(endpoint)) {
            serverList = "";
        }
    }

    @Override
    public Service selectOneService(String serviceName) throws NacosException {
        return selectOneService(serviceName, Constants.DEFAULT_GROUP);
    }

    @Override
    public Service selectOneService(String serviceName, String groupName) throws NacosException {
        return serverProxy.queryService(serviceName, groupName);
    }

    @Override
    public void createService(String serviceName) throws NacosException {
        createService(serviceName, Constants.DEFAULT_GROUP);
    }

    @Override
    public void createService(String serviceName, String groupName) throws NacosException {
        createService(serviceName, groupName, Constants.PROTECT_THRESHOLD);
    }

    @Override
    public void createService(String serviceName, String groupName, Float protectThreshold) throws NacosException {
        NoneSelector selector = new NoneSelector();
        Service service = new Service();
        service.setName(serviceName);
        service.setGroupName(groupName);
        service.setProtectThreshold(protectThreshold);

        createService(service, selector);
    }

    @Override
    public void createService(String serviceName, String groupName, Float protectThreshold, String expression) throws NacosException {
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
    public void updateService(String serviceName, String groupName, Float protectThreshold) throws NacosException {
        Service service = new Service();
        service.setName(serviceName);
        service.setGroupName(groupName);
        service.setProtectThreshold(protectThreshold);

        updateService(service, new NoneSelector());
    }

    @Override
    public void updateService(String serviceName, String groupName, Float protectThreshold, Map<String, String> metadata) throws NacosException {
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

}
