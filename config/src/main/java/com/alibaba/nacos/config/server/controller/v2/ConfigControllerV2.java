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
import com.alibaba.nacos.config.server.model.vo.ConfigRequestInfoVo;
import com.alibaba.nacos.config.server.model.vo.ConfigVo;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
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
    
    private final ConfigServletInner inner;
    
    private final ConfigOperationService configOperationService;
    
    public ConfigControllerV2(ConfigServletInner inner, ConfigOperationService configOperationService) {
        this.inner = inner;
        this.configOperationService = configOperationService;
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
     * @throws NacosException NacosException.
     */
    @PostMapping()
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    public Result<Boolean> publishConfig(ConfigVo configVo, HttpServletRequest request) throws NacosException {
        // check required field
        configVo.validate();
        // encrypted
        Pair<String, String> pair = EncryptionHandler.encryptHandler(configVo.getDataId(), configVo.getContent());
        configVo.setContent(pair.getSecond());
        // check param
        ParamUtils.checkTenantV2(configVo.getTenant());
        ParamUtils.checkParamV2(configVo.getDataId(), configVo.getGroup(), "datumId", configVo.getContent());
        ParamUtils.checkParamV2(configVo.getTag());
    
        if (StringUtils.isBlank(configVo.getSrcUser())) {
            configVo.setSrcUser(RequestUtil.getSrcUserName(request));
        }
        if (!ConfigType.isValidType(configVo.getType())) {
            configVo.setType(ConfigType.getDefaultType().getType());
        }
    
        Map<String, Object> configAdvanceInfo = configOperationService.getConfigAdvanceInfo(configVo);
        ParamUtils.checkParamV2(configAdvanceInfo);
    
        ConfigRequestInfoVo configRequestInfoVo = new ConfigRequestInfoVo();
        configRequestInfoVo.setSrcIp(RequestUtil.getRemoteIp(request));
        configRequestInfoVo.setRequestIpApp(RequestUtil.getAppName(request));
        configRequestInfoVo.setBetaIps(request.getHeader("betaIps"));
    
        String encryptedDataKey = pair.getFirst();
    
        return Result.success(configOperationService
                .publishConfig(configVo, configRequestInfoVo, configAdvanceInfo, encryptedDataKey, true));
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
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "tag", required = false) String tag) throws NacosApiException {
        // check tenant
        ParamUtils.checkTenantV2(tenant);
        ParamUtils.checkParamV2(dataId, group, "datumId", "rm");
        ParamUtils.checkParamV2(tag);
        
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        return Result.success(configOperationService.deleteConfig(dataId, group, tenant, tag, clientIp, srcUser));
    }
}
