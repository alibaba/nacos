/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Config clone service.
 *
 * @author xiweng.yy
 */
@Service
public class ConfigCloneService {
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final NamespacePersistService namespacePersistService;
    
    public ConfigCloneService(ConfigInfoPersistService configInfoPersistService,
        NamespacePersistService namespacePersistService) {
        this.configInfoPersistService = configInfoPersistService;
        this.namespacePersistService = namespacePersistService;
    }
    
    /**
     * Clone configs from source namespace to target namespace.
     *
     * @param sourceNamespaceId source namespace ID.
     * @param targetNamespaceId target namespace ID.
     * @param cloneItems        clone item list.
     * @param srcUser           source user.
     * @param policy            same config policy.
     * @param srcIp             source IP.
     * @param requestIpApp      request app.
     * @return clone result.
     * @throws NacosException if clone failed.
     */
    public Result<Map<String, Object>> cloneConfig(String sourceNamespaceId,
        String targetNamespaceId, List<ConfigCloneItem> cloneItems, String srcUser,
        SameConfigPolicy policy, String srcIp, String requestIpApp) throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        if (CollectionUtils.isEmpty(cloneItems)) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NO_SELECTED_CONFIG, failedData);
        }
        List<ConfigCloneItem> validCloneItems = new ArrayList<>(cloneItems.size());
        for (ConfigCloneItem cloneItem : cloneItems) {
            if (cloneItem != null) {
                validCloneItems.add(cloneItem);
            }
        }
        if (validCloneItems.isEmpty()) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NO_SELECTED_CONFIG, failedData);
        }
        
        String normalizedTargetNamespaceId =
            NamespaceUtil.processNamespaceParameter(targetNamespaceId);
        String normalizedSourceNamespaceId = StringUtils.isBlank(sourceNamespaceId)
            ? normalizedTargetNamespaceId : NamespaceUtil.processNamespaceParameter(
                sourceNamespaceId);
        if (isNamespaceNotExist(normalizedSourceNamespaceId) || isNamespaceNotExist(
            normalizedTargetNamespaceId)) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NAMESPACE_NOT_EXIST, failedData);
        }
        
        List<Long> idList = new ArrayList<>(validCloneItems.size());
        Map<Long, ConfigCloneItem> cloneItemsMap = validCloneItems.stream()
            .collect(Collectors.toMap(ConfigCloneItem::getConfigId, item -> {
                idList.add(item.getConfigId());
                return item;
            }, (first, second) -> first));
        
        List<ConfigAllInfo> queryedDataList = configInfoPersistService
            .findAllConfigInfo4Export(null, null, normalizedSourceNamespaceId, null, idList);
        
        if (queryedDataList == null || queryedDataList.isEmpty()) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.DATA_EMPTY, failedData);
        }
        
        List<ConfigAllInfo> configInfoList4Clone = new ArrayList<>(queryedDataList.size());
        for (ConfigAllInfo ci : queryedDataList) {
            ConfigCloneItem item = cloneItemsMap.get(ci.getId());
            ConfigAllInfo ci4save = new ConfigAllInfo();
            ci4save.setTenant(normalizedTargetNamespaceId);
            ci4save.setType(ci.getType());
            ci4save.setGroup(
                item != null && StringUtils.isNotBlank(item.getTargetGroupName())
                    ? item.getTargetGroupName() : ci.getGroup());
            ci4save.setDataId(
                item != null && StringUtils.isNotBlank(item.getTargetDataId())
                    ? item.getTargetDataId() : ci.getDataId());
            ci4save.setContent(ci.getContent());
            if (StringUtils.isNotBlank(ci.getAppName())) {
                ci4save.setAppName(ci.getAppName());
            }
            ci4save.setDesc(ci.getDesc());
            ci4save.setEncryptedDataKey(
                ci.getEncryptedDataKey() == null ? StringUtils.EMPTY : ci.getEncryptedDataKey());
            configInfoList4Clone.add(ci4save);
        }
        
        final Timestamp time = TimeUtils.getCurrentTime();
        Map<String, Object> saveResult =
            configInfoPersistService.batchInsertOrUpdate(configInfoList4Clone, srcUser, srcIp,
                null, policy);
        for (ConfigInfo configInfo : configInfoList4Clone) {
            ConfigChangePublisher.notifyConfigChange(
                new ConfigDataChangeEvent(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant(), time.getTime()));
            ConfigTraceService.logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(),
                configInfo.getTenant(), requestIpApp, time.getTime(), InetUtils.getSelfIP(),
                ConfigTraceService.PERSISTENCE_EVENT, ConfigTraceService.PERSISTENCE_TYPE_PUB,
                configInfo.getContent());
        }
        return Result.success(saveResult);
    }
    
    private boolean isNamespaceNotExist(String namespaceId) {
        return StringUtils.isNotBlank(namespaceId)
            && !NamespaceUtil.isDefaultNamespaceId(namespaceId)
            && namespacePersistService.tenantInfoCountByTenantId(namespaceId) <= 0;
    }
    
    /**
     * Config clone item.
     */
    public static class ConfigCloneItem {
        
        private final Long configId;
        
        private final String targetDataId;
        
        private final String targetGroupName;
        
        public ConfigCloneItem(Long configId, String targetDataId, String targetGroupName) {
            this.configId = configId;
            this.targetDataId = targetDataId;
            this.targetGroupName = targetGroupName;
        }
        
        public Long getConfigId() {
            return configId;
        }
        
        public String getTargetDataId() {
            return targetDataId;
        }
        
        public String getTargetGroupName() {
            return targetGroupName;
        }
    }
}
