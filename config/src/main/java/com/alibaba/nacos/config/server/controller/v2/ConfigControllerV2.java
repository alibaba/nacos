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

package com.alibaba.nacos.config.server.controller.v2;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.NacosApiResponseWrap;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.MapUtil;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.ConfigServletInner;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.model.vo.ConfigVo;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * Special controller v2 for soft load client to publish data.
 *
 * @author dongyafei
 * @date 2022/7/22
 */

@NacosApi
@RestController
@RequestMapping(Constants.CONFIG_CONTROLLER_V2_PATH)
public class ConfigControllerV2 {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigControllerV2.class);
    
    private final ConfigServletInner inner;
    
    private final PersistService persistService;
    
    public ConfigControllerV2(ConfigServletInner inner, PersistService persistService) {
        this.inner = inner;
        this.persistService = persistService;
    }
    
    /**
     * Get configure board information fail.
     *
     * @throws ServletException            ServletException.
     * @throws IOException                 IOException.
     * @throws NacosApiException NacosApiException.
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public void getConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "tag", required = false) String tag)
            throws NacosApiException, IOException, ServletException {
        // check tenant
        ParamUtils.checkTenantV2(tenant);
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        // check params
        ParamUtils.checkParamV2(dataId, group, "datumId", "content");
        ParamUtils.checkParamV2(tag);
        final String clientIp = RequestUtil.getRemoteIp(request);
        String isNotify = request.getHeader("notify");
        inner.doGetConfig(request, response, dataId, group, tenant, tag, isNotify, clientIp, true);
    }
    
    /**
     * Adds or updates non-aggregated data.
     *
     * @throws NacosApiException NacosApiException.
     */
    @NacosApiResponseWrap
    @PostMapping()
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    public Boolean publishConfig(@RequestBody ConfigVo configVo, HttpServletRequest request)
            throws NacosApiException {
        
        configVo.validate();
        final String srcIp = RequestUtil.getRemoteIp(request);
        final String requestIpApp = RequestUtil.getAppName(request);
        
        String srcUser = configVo.getSrcUser();
        if (StringUtils.isBlank(srcUser)) {
            srcUser = RequestUtil.getSrcUserName(request);
        }
        //check type
        String type = configVo.getType();
        if (!ConfigType.isValidType(type)) {
            type = ConfigType.getDefaultType().getType();
        }
        
        // encrypted
        String dataId = configVo.getDataId();
        String content = configVo.getContent();
        String group = configVo.getGroup();
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
        content = pair.getSecond();
        
        // check tenant
        String tenant = configVo.getTenant();
        String tag = configVo.getTag();
        ParamUtils.checkTenantV2(tenant);
        ParamUtils.checkParamV2(dataId, group, "datumId", content);
        ParamUtils.checkParamV2(tag);
        
        Map<String, Object> configAdvanceInfo = new HashMap<>(10);
        MapUtil.putIfValNoNull(configAdvanceInfo, "config_tags", configVo.getConfigTags());
        MapUtil.putIfValNoNull(configAdvanceInfo, "desc", configVo.getDesc());
        MapUtil.putIfValNoNull(configAdvanceInfo, "use", configVo.getUse());
        MapUtil.putIfValNoNull(configAdvanceInfo, "effect", configVo.getEffect());
        MapUtil.putIfValNoNull(configAdvanceInfo, "type", configVo.getType());
        MapUtil.putIfValNoNull(configAdvanceInfo, "schema", configVo.getSchema());
        ParamUtils.checkParamV2(configAdvanceInfo);
        
        if (AggrWhitelist.isAggrDataId(dataId)) {
            LOGGER.warn("[aggr-conflict] {} attempt to publish single data, {}, {}", RequestUtil.getRemoteIp(request),
                    dataId, group);
            throw new NacosApiException(HttpStatus.FORBIDDEN.value(), ErrorCode.INVALID_DATA_ID, "dataId:" + dataId + " is aggr");
        }
        
        final Timestamp time = TimeUtils.getCurrentTime();
        String betaIps = request.getHeader("betaIps");
        String appName = configVo.getAppName();
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setType(type);
        String encryptedDataKey = pair.getFirst();
        configInfo.setEncryptedDataKey(encryptedDataKey);
        if (StringUtils.isBlank(betaIps)) {
            if (StringUtils.isBlank(tag)) {
                persistService.insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, false);
                ConfigChangePublisher
                        .notifyConfigChange(new ConfigDataChangeEvent(false, dataId, group, tenant, time.getTime()));
            } else {
                persistService.insertOrUpdateTag(configInfo, tag, srcIp, srcUser, time, false);
                ConfigChangePublisher.notifyConfigChange(
                        new ConfigDataChangeEvent(false, dataId, group, tenant, tag, time.getTime()));
            }
        } else {
            // beta publish
            configInfo.setEncryptedDataKey(encryptedDataKey);
            persistService.insertOrUpdateBeta(configInfo, betaIps, srcIp, srcUser, time, false);
            ConfigChangePublisher
                    .notifyConfigChange(new ConfigDataChangeEvent(true, dataId, group, tenant, time.getTime()));
        }
        ConfigTraceService
                .logPersistenceEvent(dataId, group, tenant, requestIpApp, time.getTime(), InetUtils.getSelfIP(),
                        ConfigTraceService.PERSISTENCE_EVENT_PUB, content);
        return true;
    }
    
    /**
     * Synchronously delete all pre-aggregation data under a dataId.
     *
     * @throws NacosException NacosException.
     */
    @NacosApiResponseWrap
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    public Boolean deleteConfig(HttpServletRequest request,
            @RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "tag", required = false) String tag) throws NacosApiException {
        // check tenant
        ParamUtils.checkTenantV2(tenant);
        ParamUtils.checkParamV2(dataId, group, "datumId", "rm");
        ParamUtils.checkParamV2(tag);
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
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
}
