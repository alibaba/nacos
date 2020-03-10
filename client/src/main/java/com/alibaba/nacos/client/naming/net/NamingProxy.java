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
package com.alibaba.nacos.client.naming.net;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.SubscribeInfo;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.connection.ServerListManager;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
import com.alibaba.nacos.client.security.SecurityProxy;

import java.util.Properties;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * @author nkorange
 */
public class NamingProxy {

    private String namespaceId;

    private SecurityProxy securityProxy;

    private NamingGrpcClient namingGrpcClient;

    private NamingHttpClient namingHttpClient;

    private ServerListManager serverListManager;

    public NamingProxy(String namespaceId, String endpoint, String serverList, Properties properties) {
        this.serverListManager = new ServerListManager(endpoint, serverList);
        this.securityProxy = new SecurityProxy(properties);
        this.namespaceId = namespaceId;
        this.namingHttpClient = new NamingHttpClient(serverListManager, securityProxy);
    }

    public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {

        NAMING_LOGGER.info("[REGISTER-SERVICE] {} registering service {} with instance: {}",
            namespaceId, serviceName, instance);
        getNamingClient().registerInstance(namespaceId, serviceName, groupName, instance);
    }

    public void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException {

        NAMING_LOGGER.info("[DEREGISTER-SERVICE] {} deregistering service {} with instance: {}",
            namespaceId, serviceName, instance);

        getNamingClient().deregisterInstance(namespaceId, serviceName, groupName, instance);
    }

    public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER.info("[UPDATE-SERVICE] {} update service {} with instance: {}",
            namespaceId, serviceName, instance);

        getNamingClient().updateInstance(namespaceId, serviceName, groupName, instance);
    }

    public Service queryService(String serviceName, String groupName) throws NacosException {
        NAMING_LOGGER.info("[QUERY-SERVICE] {} query service : {}, {}",
            namespaceId, serviceName, groupName);

        return getNamingClient().queryService(namespaceId, serviceName, groupName);
    }

    public void createService(Service service, AbstractSelector selector) throws NacosException {

        NAMING_LOGGER.info("[CREATE-SERVICE] {} creating service : {}",
            namespaceId, service);

        getNamingClient().createService(namespaceId, service, selector);
    }

    public boolean deleteService(String serviceName, String groupName) throws NacosException {
        NAMING_LOGGER.info("[DELETE-SERVICE] {} deleting service : {} with groupName : {}",
            namespaceId, serviceName, groupName);

        return getNamingClient().deleteService(namespaceId, serviceName, groupName);
    }

    public void updateService(Service service, AbstractSelector selector) throws NacosException {
        NAMING_LOGGER.info("[UPDATE-SERVICE] {} updating service : {}",
            namespaceId, service);

        getNamingClient().updateService(namespaceId, service, selector);
    }

    public String queryList(String serviceName, String groupName, String clusters, SubscribeInfo subscribeInfo, boolean healthyOnly)
        throws NacosException {

        return getNamingClient().queryList(namespaceId, serviceName, groupName, clusters, subscribeInfo, healthyOnly);
    }

    public String queryList(String serviceFullName, String clusters, SubscribeInfo subscribeInfo, boolean healthyOnly)
        throws NacosException {

        return getNamingClient().queryList(namespaceId, NamingUtils.getServiceName(serviceFullName),
            NamingUtils.getGroupName(serviceFullName), clusters, subscribeInfo, healthyOnly);
    }

    public JSONObject sendBeat(BeatInfo beatInfo, boolean lightBeatEnabled) throws NacosException {

        if (NAMING_LOGGER.isDebugEnabled()) {
            NAMING_LOGGER.debug("[BEAT] {} sending beat to server: {}", namespaceId, beatInfo.toString());
        }
        return getNamingClient().sendBeat(namespaceId, beatInfo, lightBeatEnabled);
    }

    public boolean serverHealthy() {
        return getNamingClient().serverHealthy();
    }

    public ListView<String> getServiceList(int pageNo, int pageSize, String groupName) throws NacosException {
        return getServiceList(pageNo, pageSize, groupName, null);
    }

    public ListView<String> getServiceList(int pageNo, int pageSize, String groupName, AbstractSelector selector) throws NacosException {
        return getNamingClient().getServiceList(namespaceId, pageNo, pageSize, groupName, selector);
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    private NamingClient getNamingClient() {
        // TODO choose a client:
        return namingHttpClient;
    }

}

