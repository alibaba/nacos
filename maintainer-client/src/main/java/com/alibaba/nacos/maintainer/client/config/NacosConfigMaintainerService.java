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

package com.alibaba.nacos.maintainer.client.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.model.config.Capacity;
import com.alibaba.nacos.maintainer.client.model.config.ConfigAdvanceInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigAllInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigHistoryInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfo4Beta;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfoWrapper;
import com.alibaba.nacos.maintainer.client.model.config.GroupkeyListenserStatus;
import com.alibaba.nacos.maintainer.client.model.config.Page;
import com.alibaba.nacos.maintainer.client.model.config.SameConfigPolicy;
import com.alibaba.nacos.maintainer.client.model.config.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.maintainer.client.model.core.Connection;
import com.alibaba.nacos.maintainer.client.model.core.IdGeneratorVO;
import com.alibaba.nacos.maintainer.client.model.core.Member;
import com.alibaba.nacos.maintainer.client.model.core.ServerLoaderMetrics;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration management.
 *
 * @author Nacos
 */
public class NacosConfigMaintainerService implements ConfigMaintainerService {
    
    private final ClientHttpProxy clientHttpProxy;
    
    public NacosConfigMaintainerService(Properties properties) throws NacosException {
        this.clientHttpProxy = new ClientHttpProxy(properties);
    }
    
    @Override
    public ConfigAllInfo getConfig(String dataId, String groupName) throws Exception {
        return getConfig(dataId, groupName, ParamUtil.getDefaultNamespaceId());
    }
    
    @Override
    public ConfigAllInfo getConfig(String dataId, String groupName, String namespaceId) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeHttpRequest(httpRequest);
        Result<ConfigAllInfo> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ConfigAllInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public boolean publishConfig(String dataId, String groupName, String content) throws NacosException {
        return false;
    }
    
    @Override
    public boolean publishConfig(String dataId, String groupName, String namespaceId, String content)
            throws NacosException {
        return false;
    }
    
    @Override
    public boolean publishConfig(String dataId, String groupName, String namespaceId, String content, String tag,
            String appName, String srcUser, String configTags, String desc, String use, String effect, String type,
            String schema) throws NacosException {
        return false;
    }
    
    @Override
    public boolean deleteConfig(String dataId, String groupName, String namespaceId) throws NacosException {
        return false;
    }
    
    @Override
    public boolean deleteConfig(String dataId, String groupName, String namespaceId, String tag) throws NacosException {
        return false;
    }
    
    @Override
    public boolean deleteConfigs(List<Long> ids) throws NacosException {
        return false;
    }
    
    @Override
    public ConfigAdvanceInfo getConfigAdvanceInfo(String dataId, String groupName, String namespaceId)
            throws NacosException {
        return null;
    }
    
    @Override
    public GroupkeyListenserStatus getListeners(String dataId, String groupName, String namespaceId) throws Exception {
        return null;
    }
    
    @Override
    public GroupkeyListenserStatus getListeners(String dataId, String groupName, String namespaceId, int sampleTime)
            throws Exception {
        return null;
    }
    
    @Override
    public Page<ConfigInfo> searchConfigByDetails(String dataId, String groupName, String namespaceId,
            String configDetail, String search, int pageNo, int pageSize) throws NacosException {
        return null;
    }
    
    @Override
    public boolean stopBeta(String dataId, String groupName) throws NacosException {
        return false;
    }
    
    @Override
    public boolean stopBeta(String dataId, String groupName, String namespaceId) throws NacosException {
        return false;
    }
    
    @Override
    public ConfigInfo4Beta queryBeta(String dataId, String groupName) throws NacosException {
        return null;
    }
    
    @Override
    public ConfigInfo4Beta queryBeta(String dataId, String groupName, String namespaceId) throws NacosException {
        return null;
    }
    
    @Override
    public Map<String, Object> importAndPublishConfig(String namespaceId, String srcUser, SameConfigPolicy policy,
            MultipartFile file) throws NacosException {
        return Map.of();
    }
    
    @Override
    public ResponseEntity<byte[]> exportConfig(String dataId, String groupName, String namespaceId, List<Long> ids)
            throws NacosException {
        return null;
    }
    
    @Override
    public Map<String, Object> cloneConfig(String namespaceId, List<SameNamespaceCloneConfigBean> configBeansList,
            String srcUser, SameConfigPolicy policy) throws NacosException {
        return new HashMap<>();
    }
    
    @Override
    public Page<ConfigHistoryInfo> listConfigHistory(String dataId, String groupName, String namespaceId, int pageNo,
            int pageSize) throws NacosApiException {
        return null;
    }
    
    @Override
    public ConfigHistoryInfo getConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long nid)
            throws NacosApiException {
        return null;
    }
    
    @Override
    public ConfigHistoryInfo getPreviousConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long id)
            throws NacosApiException {
        return null;
    }
    
    @Override
    public List<ConfigInfoWrapper> getConfigListByNamespace(String namespaceId) throws NacosApiException {
        return List.of();
    }
    
    @Override
    public Capacity getCapacityWithDefault(String groupName, String namespaceId) throws NacosApiException {
        return null;
    }
    
    @Override
    public void initCapacity(String groupName, String namespaceId) throws NacosApiException {
    
    }
    
    @Override
    public boolean insertOrUpdateCapacity(String groupName, String namespaceId, Integer quota, Integer maxSize,
            Integer maxAggrCount, Integer maxAggrSize) throws NacosApiException {
        return false;
    }
    
    @Override
    public String updateLocalCacheFromStore() {
        return "";
    }
    
    @Override
    public String setLogLevel(String logName, String logLevel) {
        return "";
    }
    
    @Override
    public Object derbyOps(String sql) {
        return null;
    }
    
    @Override
    public DeferredResult<Result<String>> importDerby(MultipartFile multipartFile) {
        return null;
    }
    
    @Override
    public GroupkeyListenserStatus getAllSubClientConfigByIp(String ip, boolean all, String namespaceId,
            int sampleTime) {
        return null;
    }
    
    @Override
    public Map<String, Object> getClientMetrics(String ip, String dataId, String groupName, String namespaceId)
            throws NacosException {
        return new HashMap<>();
    }
    
    @Override
    public Map<String, Object> getClusterMetrics(String ip, String dataId, String groupName, String namespaceId)
            throws NacosException {
        return new HashMap<>();
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