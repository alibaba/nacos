/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.maintainer.client.model.core.Connection;
import com.alibaba.nacos.maintainer.client.model.core.IdGeneratorVO;
import com.alibaba.nacos.maintainer.client.model.core.Member;
import com.alibaba.nacos.maintainer.client.model.core.ServerLoaderMetrics;
import com.alibaba.nacos.maintainer.client.model.naming.InstanceDetailInfoVo;
import com.alibaba.nacos.maintainer.client.model.naming.InstanceMetadataBatchOperationVo;
import com.alibaba.nacos.maintainer.client.model.naming.MetricsInfoVo;
import com.alibaba.nacos.maintainer.client.model.naming.ServiceDetailInfo;
import com.alibaba.nacos.maintainer.client.model.naming.SwitchDomain;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Naming management.
 *
 * @author Nacos
 */
public class NacosNamingMaintainerService implements NamingMaintainerService {
    
    private final ClientHttpProxy clientHttpProxy;
    
    public NacosNamingMaintainerService(Properties properties) throws NacosException {
        this.clientHttpProxy = new ClientHttpProxy(properties);
    }
    
    @Override
    public String createService(String serviceName) throws NacosException {
        return "";
    }
    
    @Override
    public String createService(String namespaceId, String groupName, String serviceName, String metadata,
            boolean ephemeral, float protectThreshold, String selector) throws NacosException {
        return "";
    }
    
    @Override
    public String updateService(String namespaceId, String groupName, String serviceName, String metadata,
            boolean ephemeral, float protectThreshold, String selector) throws Exception {
        return "";
    }
    
    @Override
    public String removeService(String namespaceId, String groupName, String serviceName) throws NacosException {
        return "";
    }
    
    @Override
    public ServiceDetailInfo getServiceDetail(String namespaceId, String groupName, String serviceName)
            throws NacosException {
        return null;
    }
    
    @Override
    public Object listServices(String namespaceId, String groupName, String selector, int pageNo, int pageSize)
            throws Exception {
        return null;
    }
    
    @Override
    public ObjectNode searchServiceNames(String namespaceId, String expr) throws NacosException {
        return null;
    }
    
    @Override
    public Result<ObjectNode> getSubscribers(String namespaceId, String groupName, String serviceName, int pageNo,
            int pageSize, boolean aggregation) throws NacosException {
        return null;
    }
    
    @Override
    public List<String> listSelectorTypes() {
        return List.of();
    }
    
    @Override
    public SwitchDomain getSwitches() {
        return null;
    }
    
    @Override
    public String updateSwitch(String entry, String value, boolean debug) throws Exception {
        return "";
    }
    
    @Override
    public MetricsInfoVo getMetrics(boolean onlyStatus) {
        return null;
    }
    
    @Override
    public String setLogLevel(String logName, String logLevel) {
        return "";
    }
    
    @Override
    public String registerInstance(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws NacosException {
        return "";
    }
    
    @Override
    public String deregisterInstance(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws NacosException {
        return "";
    }
    
    @Override
    public String updateInstance(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws NacosException {
        return "";
    }
    
    @Override
    public InstanceMetadataBatchOperationVo batchUpdateInstanceMetadata(String namespaceId, String groupName,
            String serviceName, String instance, Map<String, String> metadata, String consistencyType)
            throws NacosException {
        return null;
    }
    
    @Override
    public InstanceMetadataBatchOperationVo batchDeleteInstanceMetadata(String namespaceId, String groupName,
            String serviceName, String instance, Map<String, String> metadata, String consistencyType)
            throws NacosException {
        return null;
    }
    
    @Override
    public String partialUpdateInstance(String namespaceId, String serviceName, String clusterName, int ip, int port,
            double weight, boolean enabled, String metadata) throws NacosException {
        return "";
    }
    
    @Override
    public ServiceInfo listInstances(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, boolean healthyOnly) throws NacosException {
        return null;
    }
    
    @Override
    public InstanceDetailInfoVo getInstanceDetail(String namespaceId, String groupName, String serviceName,
            String clusterName, String ip, int port) throws NacosException {
        return null;
    }
    
    @Override
    public String updateInstanceHealthStatus(String namespaceId, String groupName, String serviceName,
            String clusterName, String metadata, boolean ephemeral, float protectThreshold, String selector)
            throws NacosException {
        return "";
    }
    
    @Override
    public Map<String, AbstractHealthChecker> getHealthCheckers() {
        return Map.of();
    }
    
    @Override
    public String updateCluster(String namespaceId, String groupName, String clusterName, Integer checkPort,
            Boolean useInstancePort4Check, String healthChecker, Map<String, String> metadata) throws Exception {
        return "";
    }
    
    @Override
    public List<String> getClientList() {
        return List.of();
    }
    
    @Override
    public ObjectNode getClientDetail(String clientId) throws NacosException {
        return null;
    }
    
    @Override
    public List<ObjectNode> getPublishedServiceList(String clientId) throws NacosException {
        return List.of();
    }
    
    @Override
    public List<ObjectNode> getSubscribeServiceList(String clientId) throws NacosException {
        return List.of();
    }
    
    @Override
    public List<ObjectNode> getPublishedClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) throws NacosException {
        return List.of();
    }
    
    @Override
    public List<ObjectNode> getSubscribeClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) throws NacosException {
        return List.of();
    }
    
    @Override
    public ObjectNode getResponsibleServerForClient(String ip, String port) {
        return null;
    }
    
    @Override
    public String raftOps(String command, String value, String groupId) {
        return "";
    }
    
    @Override
    public List<IdGeneratorVO> getIdsHealth() {
        return List.of();
    }
    
    @Override
    public void updateLogLevel(String logName, String logLevel) {
    
    }
    
    @Override
    public Member getSelfNode() {
        return null;
    }
    
    @Override
    public Collection<Member> listClusterNodes(String address, String state) throws NacosException {
        return List.of();
    }
    
    @Override
    public String getSelfNodeHealth() {
        return "";
    }
    
    @Override
    public Boolean updateClusterNodes(List<Member> nodes) throws NacosApiException {
        return null;
    }
    
    @Override
    public Boolean updateLookupMode(String type) throws NacosException {
        return null;
    }
    
    @Override
    public Map<String, Connection> getCurrentClients() {
        return Map.of();
    }
    
    @Override
    public String reloadConnectionCount(Integer count, String redirectAddress) {
        return "";
    }
    
    @Override
    public String smartReloadCluster(String loaderFactorStr) {
        return "";
    }
    
    @Override
    public String reloadSingleClient(String connectionId, String redirectAddress) {
        return "";
    }
    
    @Override
    public ServerLoaderMetrics getClusterLoaderMetrics() {
        return null;
    }
}