/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.inner.core;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.handler.core.PluginHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.cluster.remote.request.PluginAvailabilityRequest;
import com.alibaba.nacos.core.cluster.remote.response.PluginAvailabilityResponse;
import com.alibaba.nacos.core.plugin.PluginManager;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Inner implementation of PluginHandler for handling plugin-related operations.
 *
 * @author WangzJi
 */
@Service
@EnabledInnerHandler
public class PluginInnerHandler implements PluginHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginInnerHandler.class);
    
    private final PluginManager pluginManager;
    
    private final ServerMemberManager memberManager;
    
    private final ClusterRpcClientProxy rpcClientProxy;
    
    public PluginInnerHandler(PluginManager pluginManager, ServerMemberManager memberManager,
        ClusterRpcClientProxy rpcClientProxy) {
        this.pluginManager = pluginManager;
        this.memberManager = memberManager;
        this.rpcClientProxy = rpcClientProxy;
    }
    
    @Override
    public List<PluginInfoVO> listPlugins(String pluginType) throws NacosException {
        List<PluginInfoVO> localList = pluginManager.listAllPlugins().stream()
            .filter(p -> StringUtils.isBlank(pluginType)
                || pluginType.equals(p.getPluginType().getType()))
            .map(this::convertToVO)
            .collect(Collectors.toList());
        
        Collection<Member> members = memberManager.allMembers();
        int totalNodeCount = members.size();
        Map<String, LongAdder> availableCountMap = new ConcurrentHashMap<>(localList.size());
        
        List<CompletableFuture<Void>> futures = members.stream()
            .map(member -> CompletableFuture.runAsync(() -> {
                Map<String, Boolean> memberAvailability = queryMemberAvailability(member);
                if (memberAvailability != null) {
                    memberAvailability.forEach((pluginId, available) -> {
                        if (Boolean.TRUE.equals(available)) {
                            availableCountMap.computeIfAbsent(pluginId, k -> new LongAdder())
                                .increment();
                        }
                    });
                }
            }))
            .collect(Collectors.toList());
        
        awaitCompletion(futures);
        
        localList.forEach(vo -> {
            vo.setTotalNodeCount(totalNodeCount);
            LongAdder adder = availableCountMap.get(vo.getPluginId());
            vo.setAvailableNodeCount(adder != null ? adder.intValue() : 0);
        });
        
        return localList;
    }
    
    private Map<String, Boolean> queryMemberAvailability(Member member) {
        try {
            if (memberManager.getSelf().equals(member)) {
                return pluginManager.listAllPlugins().stream()
                    .collect(Collectors.toMap(PluginInfo::getPluginId, PluginInfo::isEnabled));
            }
            
            if (!NodeState.UP.equals(member.getState())) {
                return null;
            }
            
            PluginAvailabilityRequest request = new PluginAvailabilityRequest();
            request.setQueryAll(true);
            
            PluginAvailabilityResponse response =
                (PluginAvailabilityResponse) rpcClientProxy.sendRequest(
                    member, request);
            if (response == null) {
                LOGGER.warn("Received null response when querying plugin availability from node {}",
                    member.getAddress());
                return null;
            }
            return response.getPluginAvailabilityMap();
        } catch (Exception e) {
            LOGGER.warn("Failed to query plugin availability from node {}", member.getAddress(), e);
            return null;
        }
    }
    
    @Override
    public PluginDetailVO getPluginDetail(String pluginType, String pluginName)
        throws NacosException {
        String pluginId = pluginType + ":" + pluginName;
        return pluginManager.getPlugin(pluginId)
            .map(this::convertToDetailVO)
            .orElseThrow(() -> new NacosApiException(HttpStatus.NOT_FOUND.value(),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Plugin not found: " + pluginId));
    }
    
    @Override
    public void updatePluginStatus(String pluginType, String pluginName, boolean enabled,
        boolean localOnly)
        throws NacosException {
        String pluginId = pluginType + ":" + pluginName;
        pluginManager.setPluginEnabled(pluginId, enabled, localOnly);
    }
    
    @Override
    public void updatePluginConfig(String pluginType, String pluginName, Map<String, String> config,
        boolean localOnly) throws NacosException {
        String pluginId = pluginType + ":" + pluginName;
        pluginManager.updatePluginConfig(pluginId, config, localOnly);
    }
    
    @Override
    public Map<String, Boolean> getPluginAvailability(String pluginType, String pluginName)
        throws NacosException {
        String pluginId = pluginType + ":" + pluginName;
        
        if (!pluginManager.isPluginAvailable(pluginId)) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                "Plugin not found: " + pluginId);
        }
        
        Collection<Member> members = memberManager.allMembers();
        Map<String, Boolean> nodeAvailability = new ConcurrentHashMap<>(members.size());
        
        List<CompletableFuture<Void>> futures = members.stream()
            .map(member -> CompletableFuture.runAsync(() -> {
                String address = member.getAddress();
                nodeAvailability.put(address, checkMemberPluginAvailability(member, pluginId));
            }))
            .collect(Collectors.toList());
        
        awaitCompletion(futures);
        
        return nodeAvailability;
    }
    
    private boolean checkMemberPluginAvailability(Member member, String pluginId) {
        if (memberManager.getSelf().equals(member)) {
            return pluginManager.isPluginAvailable(pluginId);
        }
        
        if (!NodeState.UP.equals(member.getState())) {
            return false;
        }
        
        try {
            PluginAvailabilityRequest request = new PluginAvailabilityRequest();
            request.setPluginId(pluginId);
            
            PluginAvailabilityResponse response =
                (PluginAvailabilityResponse) rpcClientProxy.sendRequest(
                    member, request);
            if (response == null) {
                LOGGER.warn(
                    "Received null response when querying plugin {} availability from node {}",
                    pluginId, member.getAddress());
                return false;
            }
            return response.isAvailable();
        } catch (Exception e) {
            LOGGER.warn("Failed to query plugin {} availability from node {}: {}",
                pluginId, member.getAddress(), e.getMessage());
            return false;
        }
    }
    
    private void awaitCompletion(List<CompletableFuture<Void>> futures) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOGGER.warn("Timeout waiting for plugin availability responses from some nodes");
        } catch (Exception e) {
            LOGGER.error("Error collecting plugin availability from cluster", e);
        }
    }
    
    private PluginInfoVO convertToVO(PluginInfo pluginInfo) {
        PluginInfoVO vo = new PluginInfoVO();
        vo.setPluginId(pluginInfo.getPluginId());
        vo.setPluginType(pluginInfo.getPluginType().getType());
        vo.setPluginName(pluginInfo.getPluginName());
        vo.setEnabled(pluginInfo.isEnabled());
        vo.setCritical(pluginInfo.isCritical());
        vo.setConfigurable(pluginInfo.isConfigurable());
        vo.setExclusive(isExclusiveType(pluginInfo.getPluginType()));
        return vo;
    }
    
    /**
     * Check if the plugin type is exclusive (only one can be active at a time).
     * Exclusive types: AUTH, DATASOURCE_DIALECT.
     *
     * TODO: first return fixed {@code true}, will read from plugin define in future.
     *
     * @param type plugin type
     * @return true if exclusive
     */
    private boolean isExclusiveType(PluginType type) {
        return true;
    }
    
    private PluginDetailVO convertToDetailVO(PluginInfo pluginInfo) {
        PluginDetailVO vo = new PluginDetailVO();
        vo.setPluginId(pluginInfo.getPluginId());
        vo.setPluginType(pluginInfo.getPluginType().getType());
        vo.setPluginName(pluginInfo.getPluginName());
        vo.setEnabled(pluginInfo.isEnabled());
        vo.setCritical(pluginInfo.isCritical());
        vo.setConfigurable(pluginInfo.isConfigurable());
        vo.setConfig(pluginInfo.getConfig());
        vo.setConfigDefinitions(pluginInfo.getConfigDefinitions());
        return vo;
    }
}
