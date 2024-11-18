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
import com.alibaba.nacos.common.utils.NumberUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.ConfigGrayPersistInfo;
import com.alibaba.nacos.config.server.model.gray.GrayRule;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
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
    
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigOperationService.class);
    
    public ConfigOperationService(ConfigInfoPersistService configInfoPersistService,
            ConfigInfoTagPersistService configInfoTagPersistService,
            ConfigInfoBetaPersistService configInfoBetaPersistService,
            ConfigInfoGrayPersistService configInfoGrayPersistService) {
        this.configInfoPersistService = configInfoPersistService;
        this.configInfoTagPersistService = configInfoTagPersistService;
        this.configInfoBetaPersistService = configInfoBetaPersistService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
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
        
        configForm.setEncryptedDataKey(encryptedDataKey);
        ConfigInfo configInfo = new ConfigInfo(configForm.getDataId(), configForm.getGroup(),
                configForm.getNamespaceId(), configForm.getAppName(), configForm.getContent());
        //set old md5
        if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
            configInfo.setMd5(configRequestInfo.getCasMd5());
        }
        configInfo.setType(configForm.getType());
        configInfo.setEncryptedDataKey(encryptedDataKey);
        ConfigOperateResult configOperateResult;
        
        //beta publish
        if (StringUtils.isNotBlank(configRequestInfo.getBetaIps())) {
            configForm.setGrayName(BetaGrayRule.TYPE_BETA);
            configForm.setGrayRuleExp(configRequestInfo.getBetaIps());
            configForm.setGrayVersion(BetaGrayRule.VERSION);
            persistBeta(configForm, configInfo, configRequestInfo);
            configForm.setGrayPriority(Integer.MAX_VALUE);
            publishConfigGray(BetaGrayRule.TYPE_BETA, configForm, configRequestInfo);
            return Boolean.TRUE;
        }
        // tag publish
        if (StringUtils.isNotBlank(configForm.getTag())) {
            configForm.setGrayName(TagGrayRule.TYPE_TAG + "_" + configForm.getTag());
            configForm.setGrayRuleExp(configForm.getTag());
            configForm.setGrayVersion(TagGrayRule.VERSION);
            configForm.setGrayPriority(Integer.MAX_VALUE - 1);
            persistTagv1(configForm, configInfo, configRequestInfo);
            publishConfigGray(TagGrayRule.TYPE_TAG, configForm, configRequestInfo);
            return Boolean.TRUE;
        }
        
        //formal publish
        if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
            configOperateResult = configInfoPersistService.insertOrUpdateCas(configRequestInfo.getSrcIp(),
                    configForm.getSrcUser(), configInfo, configAdvanceInfo);
            if (!configOperateResult.isSuccess()) {
                LOGGER.warn(
                        "[cas-publish-config-fail] srcIp = {}, dataId= {}, casMd5 = {}, msg = server md5 may have changed.",
                        configRequestInfo.getSrcIp(), configForm.getDataId(), configRequestInfo.getCasMd5());
                throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.RESOURCE_CONFLICT,
                        "Cas publish fail, server md5 may have changed.");
            }
        } else {
            configOperateResult = configInfoPersistService.insertOrUpdate(configRequestInfo.getSrcIp(),
                    configForm.getSrcUser(), configInfo, configAdvanceInfo);
        }
        ConfigChangePublisher.notifyConfigChange(
                new ConfigDataChangeEvent(configForm.getDataId(), configForm.getGroup(), configForm.getNamespaceId(),
                        configOperateResult.getLastModified()));
        ConfigTraceService.logPersistenceEvent(configForm.getDataId(), configForm.getGroup(),
                configForm.getNamespaceId(), configRequestInfo.getRequestIpApp(), configOperateResult.getLastModified(),
                InetUtils.getSelfIP(), ConfigTraceService.PERSISTENCE_EVENT, ConfigTraceService.PERSISTENCE_TYPE_PUB,
                configForm.getContent());
        return true;
    }
    
    private void persistTagv1(ConfigForm configForm, ConfigInfo configInfo, ConfigRequestInfo configRequestInfo)
            throws NacosApiException {
        if (!PropertyUtil.isGrayCompatibleModel()) {
            return;
        }
        
        ConfigOperateResult configOperateResult = null;
        if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
            configOperateResult = configInfoTagPersistService.insertOrUpdateTagCas(configInfo, configForm.getTag(),
                    configRequestInfo.getSrcIp(), configForm.getSrcUser());
            if (!configOperateResult.isSuccess()) {
                LOGGER.warn(
                        "[cas-publish-tag-config-fail] srcIp = {}, dataId= {}, casMd5 = {}, msg = server md5 may have changed.",
                        configRequestInfo.getSrcIp(), configForm.getDataId(), configRequestInfo.getCasMd5());
                throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.RESOURCE_CONFLICT,
                        "Cas publish tag config fail, server md5 may have changed.");
            }
        } else {
            configOperateResult = configInfoTagPersistService.insertOrUpdateTag(configInfo, configForm.getTag(),
                    configRequestInfo.getSrcIp(), configForm.getSrcUser());
        }
    }
    
    private void persistBeta(ConfigForm configForm, ConfigInfo configInfo, ConfigRequestInfo configRequestInfo)
            throws NacosApiException {
        if (!PropertyUtil.isGrayCompatibleModel()) {
            return;
        }
        ConfigOperateResult configOperateResult = null;
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
            configInfoBetaPersistService.insertOrUpdateBeta(configInfo,
                    configRequestInfo.getBetaIps(), configRequestInfo.getSrcIp(), configForm.getSrcUser());
        }

    }
    
    /**
     * publish gray config tag v2.
     *
     * @param configForm        ConfigForm
     * @param configRequestInfo ConfigRequestInfo
     * @return boolean
     * @throws NacosException NacosException.
     * @date 2024/2/5
     */
    private Boolean publishConfigGray(String grayType, ConfigForm configForm, ConfigRequestInfo configRequestInfo)
            throws NacosException {
        
        Map<String, Object> configAdvanceInfo = getConfigAdvanceInfo(configForm);
        ParamUtils.checkParam(configAdvanceInfo);
        
        ConfigGrayPersistInfo localConfigGrayPersistInfo = new ConfigGrayPersistInfo(grayType,
                configForm.getGrayVersion(), configForm.getGrayRuleExp(), configForm.getGrayPriority());
        GrayRule grayRuleStruct = GrayRuleManager.constructGrayRule(localConfigGrayPersistInfo);
        if (grayRuleStruct == null) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.CONFIG_GRAY_VERSION_INVALID,
                    ErrorCode.CONFIG_GRAY_VERSION_INVALID.getMsg());
        }
        
        if (!grayRuleStruct.isValid()) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.CONFIG_GRAY_RULE_FORMAT_INVALID,
                    ErrorCode.CONFIG_GRAY_RULE_FORMAT_INVALID.getMsg());
        }
        
        //version count check.
        if (checkGrayVersionOverMaxCount(configForm.getDataId(), configForm.getGroup(), configForm.getNamespaceId(),
                configForm.getGrayName())) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.CONFIG_GRAY_OVER_MAX_VERSION_COUNT,
                    "gray config version is over max count :" + getMaxGrayVersionCount());
        }
        
        ConfigInfo configInfo = new ConfigInfo(configForm.getDataId(), configForm.getGroup(),
                configForm.getNamespaceId(), configForm.getAppName(), configForm.getContent());
        configInfo.setType(configForm.getType());
        configInfo.setEncryptedDataKey(configForm.getEncryptedDataKey());
        
        ConfigOperateResult configOperateResult;
        
        if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
            configOperateResult = configInfoGrayPersistService.insertOrUpdateGrayCas(configInfo,
                    configForm.getGrayName(),
                    GrayRuleManager.serializeConfigGrayPersistInfo(localConfigGrayPersistInfo),
                    configRequestInfo.getSrcIp(), configForm.getSrcUser());
            if (!configOperateResult.isSuccess()) {
                LOGGER.warn(
                        "[cas-publish-gray-config-fail] srcIp = {}, dataId= {}, casMd5 = {}, grayName = {}, msg = server md5 may have changed.",
                        configRequestInfo.getSrcIp(), configForm.getDataId(), configRequestInfo.getCasMd5(),
                        configForm.getGrayName());
                throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.RESOURCE_CONFLICT,
                        "Cas publish gray config fail, server md5 may have changed.");
            }
        } else {
            configOperateResult = configInfoGrayPersistService.insertOrUpdateGray(configInfo, configForm.getGrayName(),
                    GrayRuleManager.serializeConfigGrayPersistInfo(localConfigGrayPersistInfo),
                    configRequestInfo.getSrcIp(), configForm.getSrcUser());
        }
        
        ConfigChangePublisher.notifyConfigChange(
                new ConfigDataChangeEvent(configForm.getDataId(), configForm.getGroup(), configForm.getNamespaceId(),
                        configForm.getGrayName(), configOperateResult.getLastModified()));
        
        String eventType = ConfigTraceService.PERSISTENCE_EVENT + "-" + configForm.getGrayName();
        
        ConfigTraceService.logPersistenceEvent(configForm.getDataId(), configForm.getGroup(),
                configForm.getNamespaceId(), configRequestInfo.getRequestIpApp(), configOperateResult.getLastModified(),
                InetUtils.getSelfIP(), eventType, ConfigTraceService.PERSISTENCE_TYPE_PUB, configForm.getContent());
        return true;
    }
    
    private boolean checkGrayVersionOverMaxCount(String dataId, String group, String tenant, String grayName) {
        List<String> configInfoGrays = configInfoGrayPersistService.findConfigInfoGrays(dataId, group, tenant);
        if (configInfoGrays == null || configInfoGrays.isEmpty()) {
            return false;
        } else {
            if (configInfoGrays.contains(grayName)) {
                return false;
            }
            return configInfoGrays.size() >= getMaxGrayVersionCount();
        }
    }
    
    private static final int DEFAULT_MAX_GRAY_VERSION_COUNT = 10;
    
    private int getMaxGrayVersionCount() {
        String value = EnvUtil.getProperty("nacos.config.gray.version.max.count", "");
        return NumberUtils.isDigits(value) ? NumberUtils.toInt(value) : DEFAULT_MAX_GRAY_VERSION_COUNT;
    }
    
    /**
     * Synchronously delete all pre-aggregation data under a dataId.
     */
    public Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) {
        String persistEvent = ConfigTraceService.PERSISTENCE_EVENT;
        String grayName = "";
        if (StringUtils.isBlank(tag)) {
            configInfoPersistService.removeConfigInfo(dataId, group, namespaceId, clientIp, srcUser);
        } else {
            persistEvent = ConfigTraceService.PERSISTENCE_EVENT_TAG + "-" + tag;
            grayName = TagGrayRule.TYPE_TAG + "_" + tag;
            configInfoGrayPersistService.removeConfigInfoGray(dataId, group, namespaceId, grayName, clientIp, srcUser);
            deleteConfigTagv1(dataId, group, namespaceId, tag, clientIp, srcUser);
        }
        final Timestamp time = TimeUtils.getCurrentTime();
        ConfigTraceService.logPersistenceEvent(dataId, group, namespaceId, null, time.getTime(), clientIp, persistEvent,
                ConfigTraceService.PERSISTENCE_TYPE_REMOVE, null);
        ConfigChangePublisher.notifyConfigChange(
                new ConfigDataChangeEvent(dataId, group, namespaceId, grayName, time.getTime()));
        
        return true;
    }
    
    private void deleteConfigTagv1(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) {
        if (PropertyUtil.isGrayCompatibleModel()) {
            configInfoTagPersistService.removeConfigInfoTag(dataId, group, namespaceId, tag, clientIp, srcUser);
        }
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
