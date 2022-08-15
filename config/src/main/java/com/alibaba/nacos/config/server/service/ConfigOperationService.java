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
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.model.vo.ConfigRequestInfoVo;
import com.alibaba.nacos.config.server.model.vo.ConfigVo;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
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
    
    private PersistService persistService;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigOperationService.class);
    
    public ConfigOperationService(PersistService persistService) {
        this.persistService = persistService;
    }
    
    /**
     * Adds or updates non-aggregated data.
     *
     * @throws NacosException NacosException.
     */
    public Boolean publishConfig(ConfigVo configVo, ConfigRequestInfoVo configRequestInfoVo,
            Map<String, Object> configAdvanceInfo, String encryptedDataKey, Boolean isV2) throws NacosException {
        
        if (AggrWhitelist.isAggrDataId(configVo.getDataId())) {
            LOGGER.warn("[aggr-conflict] {} attempt to publish single data, {}, {}", configRequestInfoVo.getSrcIp(),
                    configVo.getDataId(), configVo.getGroup());
            if (isV2) {
                throw new NacosApiException(HttpStatus.FORBIDDEN.value(), ErrorCode.INVALID_DATA_ID,
                        "dataId:" + configVo.getDataId() + " is aggr");
            } else {
                throw new NacosException(NacosException.NO_RIGHT, "dataId:" + configVo.getDataId() + " is aggr");
            }
        }
        
        final Timestamp time = TimeUtils.getCurrentTime();
        ConfigInfo configInfo = new ConfigInfo(configVo.getDataId(), configVo.getGroup(), configVo.getTenant(),
                configVo.getAppName(), configVo.getContent());
        
        configInfo.setType(configVo.getType());
        configInfo.setEncryptedDataKey(encryptedDataKey);
        
        if (StringUtils.isBlank(configRequestInfoVo.getBetaIps())) {
            if (StringUtils.isBlank(configVo.getTag())) {
                persistService.insertOrUpdate(configRequestInfoVo.getSrcIp(), configVo.getSrcUser(), configInfo, time,
                        configAdvanceInfo, false);
                ConfigChangePublisher.notifyConfigChange(
                        new ConfigDataChangeEvent(false, configVo.getDataId(), configVo.getGroup(),
                                configVo.getTenant(), time.getTime()));
            } else {
                persistService.insertOrUpdateTag(configInfo, configVo.getTag(), configRequestInfoVo.getSrcIp(),
                        configVo.getSrcUser(), time, false);
                ConfigChangePublisher.notifyConfigChange(
                        new ConfigDataChangeEvent(false, configVo.getDataId(), configVo.getGroup(),
                                configVo.getTenant(), configVo.getTag(), time.getTime()));
            }
        } else {
            // beta publish
            persistService
                    .insertOrUpdateBeta(configInfo, configRequestInfoVo.getBetaIps(), configRequestInfoVo.getSrcIp(),
                            configVo.getSrcUser(), time, false);
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(true, configVo.getDataId(), configVo.getGroup(), configVo.getTenant(),
                            time.getTime()));
        }
        ConfigTraceService.logPersistenceEvent(configVo.getDataId(), configVo.getGroup(), configVo.getTenant(),
                configRequestInfoVo.getRequestIpApp(), time.getTime(), InetUtils.getSelfIP(),
                ConfigTraceService.PERSISTENCE_EVENT_PUB, configVo.getContent());
        
        return true;
    }
    
    /**
     * Synchronously delete all pre-aggregation data under a dataId.
     */
    public Boolean deleteConfig(String dataId, String group, String tenant, String tag, String clientIp,
            String srcUser) {
        if (StringUtils.isBlank(tag)) {
            persistService.removeConfigInfo(dataId, group, tenant, clientIp, srcUser);
        } else {
            persistService.removeConfigInfoTag(dataId, group, tenant, tag, clientIp, srcUser);
        }
        final Timestamp time = TimeUtils.getCurrentTime();
        ConfigTraceService.logPersistenceEvent(dataId, group, tenant, null, time.getTime(), clientIp,
                ConfigTraceService.PERSISTENCE_EVENT_REMOVE, null);
        ConfigChangePublisher
                .notifyConfigChange(new ConfigDataChangeEvent(false, dataId, group, tenant, tag, time.getTime()));
        
        return true;
    }
    
    public Map<String, Object> getConfigAdvanceInfo(ConfigVo configVo) {
        Map<String, Object> configAdvanceInfo = new HashMap<>(10);
        MapUtil.putIfValNoNull(configAdvanceInfo, "config_tags", configVo.getConfigTags());
        MapUtil.putIfValNoNull(configAdvanceInfo, "desc", configVo.getDesc());
        MapUtil.putIfValNoNull(configAdvanceInfo, "use", configVo.getUse());
        MapUtil.putIfValNoNull(configAdvanceInfo, "effect", configVo.getEffect());
        MapUtil.putIfValNoNull(configAdvanceInfo, "type", configVo.getType());
        MapUtil.putIfValNoNull(configAdvanceInfo, "schema", configVo.getSchema());
        return configAdvanceInfo;
    }
}
