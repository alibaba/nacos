/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.MapUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * ConfigService.
 *
 * @author dongyafei
 * @date 2022/8/11
 */

@Service
public class ConfigOperationService {
    
    private ConfigInfoPersistService configInfoPersistService;
    
    private ConfigInfoTagPersistService configInfoTagPersistService;
    
    private ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigOperationService.class);
    
    public ConfigOperationService(ConfigInfoPersistService configInfoPersistService,
            ConfigInfoTagPersistService configInfoTagPersistService,
            ConfigInfoBetaPersistService configInfoBetaPersistService) {
        this.configInfoPersistService = configInfoPersistService;
        this.configInfoTagPersistService = configInfoTagPersistService;
        this.configInfoBetaPersistService = configInfoBetaPersistService;
    }
    
    /**
     * Adds or updates non-aggregated data.
     *
     * @throws NacosException NacosException.
     */
    public Boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo, String encryptedDataKey)
            throws NacosException {
        
        Map<String, Object> configAdvanceInfo = getConfigAdvanceInfo(configForm);
        ParamUtils.checkParam(configAdvanceInfo);
        
        if (AggrWhitelist.isAggrDataId(configForm.getDataId())) {
            LOGGER.warn("[aggr-conflict] {} attempt to publish single data, {}, {}", configRequestInfo.getSrcIp(),
                    configForm.getDataId(), configForm.getGroup());
            throw new NacosApiException(HttpStatus.FORBIDDEN.value(), ErrorCode.INVALID_DATA_ID,
                    "dataId:" + configForm.getDataId() + " is aggr");
        }
        
        ConfigInfo configInfo = new ConfigInfo(configForm.getDataId(), configForm.getGroup(),
                configForm.getNamespaceId(), configForm.getAppName(), configForm.getContent());
        //set old md5
        if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
            configInfo.setMd5(configRequestInfo.getCasMd5());
        }
        configInfo.setType(configForm.getType());
        configInfo.setEncryptedDataKey(encryptedDataKey);
        ConfigOperateResult configOperateResult;
        
        String persistEvent = ConfigTraceService.PERSISTENCE_EVENT;
        
        if (StringUtils.isBlank(configRequestInfo.getBetaIps())) {
            if (StringUtils.isBlank(configForm.getTag())) {
                if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
                    configOperateResult = configInfoPersistService.insertOrUpdateCas(configRequestInfo.getSrcIp(),
                            configForm.getSrcUser(), configInfo, configAdvanceInfo);
                    if (!configOperateResult.isSuccess()) {
                        LOGGER.warn(
                                "[cas-publish-config-fail] srcIp = {}, dataId= {}, casMd5 = {}, msg = server md5 may have changed.",
                                configRequestInfo.getSrcIp(), configForm.getDataId(), configRequestInfo.getCasMd5());
                        throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                ErrorCode.RESOURCE_CONFLICT, "Cas publish fail, server md5 may have changed.");
                    }
                } else {
                    configOperateResult = configInfoPersistService.insertOrUpdate(configRequestInfo.getSrcIp(),
                            configForm.getSrcUser(), configInfo, configAdvanceInfo);
                }
                ConfigChangePublisher.notifyConfigChange(
                        new ConfigDataChangeEvent(false, configForm.getDataId(), configForm.getGroup(),
                                configForm.getNamespaceId(), configOperateResult.getLastModified()));
            } else {
                if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
                    configOperateResult = configInfoTagPersistService.insertOrUpdateTagCas(configInfo,
                            configForm.getTag(), configRequestInfo.getSrcIp(), configForm.getSrcUser());
                    if (!configOperateResult.isSuccess()) {
                        LOGGER.warn(
                                "[cas-publish-tag-config-fail] srcIp = {}, dataId= {}, casMd5 = {}, msg = server md5 may have changed.",
                                configRequestInfo.getSrcIp(), configForm.getDataId(), configRequestInfo.getCasMd5());
                        throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                ErrorCode.RESOURCE_CONFLICT,
                                "Cas publish tag config fail, server md5 may have changed.");
                    }
                } else {
                    configOperateResult = configInfoTagPersistService.insertOrUpdateTag(configInfo, configForm.getTag(),
                            configRequestInfo.getSrcIp(), configForm.getSrcUser());
                }
                persistEvent = ConfigTraceService.PERSISTENCE_EVENT_TAG + "-" + configForm.getTag();
                ConfigChangePublisher.notifyConfigChange(
                        new ConfigDataChangeEvent(false, configForm.getDataId(), configForm.getGroup(),
                                configForm.getNamespaceId(), configForm.getTag(),
                                configOperateResult.getLastModified()));
            }
        } else {
            // beta publish
            if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
                configOperateResult = configInfoBetaPersistService.insertOrUpdateBetaCas(configInfo,
                        configRequestInfo.getBetaIps(), configRequestInfo.getSrcIp(), configForm.getSrcUser());
                if (!configOperateResult.isSuccess()) {
                    LOGGER.warn(
                            "[cas-publish-beta-config-fail] srcIp = {}, dataId= {}, casMd5 = {}, msg = server md5 may have changed.",
                            configRequestInfo.getSrcIp(), configForm.getDataId(), configRequestInfo.getCasMd5());
                    throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.RESOURCE_CONFLICT,
                            "Cas publish beta config fail, server md5 may have changed.");
                }
            } else {
                configOperateResult = configInfoBetaPersistService.insertOrUpdateBeta(configInfo,
                        configRequestInfo.getBetaIps(), configRequestInfo.getSrcIp(), configForm.getSrcUser());
            }
            persistEvent = ConfigTraceService.PERSISTENCE_EVENT_BETA;
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(true, configForm.getDataId(), configForm.getGroup(),
                            configForm.getNamespaceId(), configOperateResult.getLastModified()));
        }
        ConfigTraceService.logPersistenceEvent(configForm.getDataId(), configForm.getGroup(),
                configForm.getNamespaceId(), configRequestInfo.getRequestIpApp(), configOperateResult.getLastModified(),
                InetUtils.getSelfIP(), persistEvent, ConfigTraceService.PERSISTENCE_TYPE_PUB, configForm.getContent());
        return true;
    }
    
    /**
     * Synchronously delete all pre-aggregation data under a dataId.
     */
    public Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) {
        String persistEvent = ConfigTraceService.PERSISTENCE_EVENT;
        if (StringUtils.isBlank(tag)) {
            configInfoPersistService.removeConfigInfo(dataId, group, namespaceId, clientIp, srcUser);
        } else {
            persistEvent = ConfigTraceService.PERSISTENCE_EVENT_TAG + "-" + tag;
            configInfoTagPersistService.removeConfigInfoTag(dataId, group, namespaceId, tag, clientIp, srcUser);
        }
        final Timestamp time = TimeUtils.getCurrentTime();
        ConfigTraceService.logPersistenceEvent(dataId, group, namespaceId, null, time.getTime(), clientIp, persistEvent,
                ConfigTraceService.PERSISTENCE_TYPE_REMOVE, null);
        ConfigChangePublisher.notifyConfigChange(
                new ConfigDataChangeEvent(false, dataId, group, namespaceId, tag, time.getTime()));
        
        return true;
    }
    
    public Map<String, Object> getConfigAdvanceInfo(ConfigForm configForm) {
        Map<String, Object> configAdvanceInfo = new HashMap<>(10);
        MapUtil.putIfValNoNull(configAdvanceInfo, "config_tags", configForm.getConfigTags());
        MapUtil.putIfValNoNull(configAdvanceInfo, "desc", configForm.getDesc());
        MapUtil.putIfValNoNull(configAdvanceInfo, "use", configForm.getUse());
        MapUtil.putIfValNoNull(configAdvanceInfo, "effect", configForm.getEffect());
        MapUtil.putIfValNoNull(configAdvanceInfo, "type", configForm.getType());
        MapUtil.putIfValNoNull(configAdvanceInfo, "schema", configForm.getSchema());
        return configAdvanceInfo;
    }
}
