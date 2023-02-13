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
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.ConfigServletInner;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigDetailService configDetailService;
    
    public ConfigControllerV2(ConfigServletInner inner, ConfigOperationService configOperationService, ConfigDetailService configDetailService) {
        this.inner = inner;
        this.configOperationService = configOperationService;
        this.configDetailService = configDetailService;
    }
    
    /**
     * Get configure board information fail.
     *
     * @throws ServletException  ServletException.
     * @throws IOException       IOException.
     * @throws NacosApiException NacosApiException.
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public void getConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "tag", required = false) String tag)
            throws NacosException, IOException, ServletException {
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        ParamUtils.checkParamV2(tag);
        final String clientIp = RequestUtil.getRemoteIp(request);
        String isNotify = request.getHeader("notify");
        inner.doGetConfig(request, response, dataId, group, namespaceId, tag, isNotify, clientIp, true);
    }
    
    /**
     * Adds or updates non-aggregated data.
     *
     * @throws NacosException NacosException.
     */
    @PostMapping()
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    public Result<Boolean> publishConfig(ConfigForm configForm, HttpServletRequest request) throws NacosException {
        // check required field
        configForm.validate();
        // encrypted
        Pair<String, String> pair = EncryptionHandler.encryptHandler(configForm.getDataId(), configForm.getContent());
        configForm.setContent(pair.getSecond());
        //fix issue #9783
        configForm.setNamespaceId(NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId()));
        // check param
        ParamUtils.checkTenantV2(configForm.getNamespaceId());
        ParamUtils.checkParam(configForm.getDataId(), configForm.getGroup(), "datumId", configForm.getContent());
        ParamUtils.checkParamV2(configForm.getTag());
    
        if (StringUtils.isBlank(configForm.getSrcUser())) {
            configForm.setSrcUser(RequestUtil.getSrcUserName(request));
        }
        if (!ConfigType.isValidType(configForm.getType())) {
            configForm.setType(ConfigType.getDefaultType().getType());
        }
    
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(RequestUtil.getRemoteIp(request));
        configRequestInfo.setRequestIpApp(RequestUtil.getAppName(request));
        configRequestInfo.setBetaIps(request.getHeader("betaIps"));
    
        String encryptedDataKey = pair.getFirst();
    
        return Result.success(configOperationService.publishConfig(configForm, configRequestInfo, encryptedDataKey));
    }
    
    /**
     * Synchronously delete all pre-aggregation data under a dataId.
     *
     * @throws NacosApiException NacosApiException.
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    public Result<Boolean> deleteConfig(HttpServletRequest request, @RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "tag", required = false) String tag) throws NacosException {
        //fix issue #9783
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        ParamUtils.checkParam(dataId, group, "datumId", "rm");
        ParamUtils.checkParamV2(tag);
        
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        return Result.success(configOperationService.deleteConfig(dataId, group, namespaceId, tag, clientIp, srcUser));
    }
    
    /**
     * search config by config detail.
     *
     */
    @GetMapping("/searchDetail")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public Page<ConfigInfo> searchConfigByDetails(@RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam(value = "config_detail") String configDetail,
            @RequestParam(value = "search", defaultValue = "blur", required = false) String search,
            @RequestParam("pageNo") int pageNo, @RequestParam("pageSize") int pageSize) throws NacosException {
        Map<String, Object> configAdvanceInfo = new HashMap<>(100);
        if (StringUtils.isNotBlank(appName)) {
            configAdvanceInfo.put("appName", appName);
        }
        if (StringUtils.isNotBlank(configTags)) {
            configAdvanceInfo.put("config_tags", configTags);
        }
        if (StringUtils.isNotBlank(configDetail)) {
            configAdvanceInfo.put("content", configDetail);
        }
        try {
            return configDetailService.findConfigInfoPage(search, pageNo, pageSize, dataId, group, tenant, configAdvanceInfo);
        } catch (Exception e) {
            String errorMsg = "serialize page error, dataId=" + dataId + ", group=" + group;
            LOGGER.error(errorMsg, e);
            throw e;
        }
    }
}
