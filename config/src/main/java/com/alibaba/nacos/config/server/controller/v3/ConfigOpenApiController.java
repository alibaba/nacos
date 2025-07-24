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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;

/**
 * Nacos config module client used HTTP Open API controller.
 *
 * <p>
 * This open API is used for some program language which not support gRPC request and want to develop a application used
 * client to get remote configuration from Nacos. So this client used open API only provide specified feature API to get
 * specified configuration. Not support listen configuration with HTTP, please use gRPC request to listen
 * configuration.
 * </p>
 *
 * @author xiweng.yy
 */
@RestController
@RequestMapping(Constants.CONFIG_V3_CLIENT_API_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ConfigOpenApiController {
    
    private final ConfigQueryChainService configQueryChainService;
    
    public ConfigOpenApiController(ConfigQueryChainService configQueryChainService) {
        this.configQueryChainService = configQueryChainService;
    }
    
    @GetMapping
    @TpsControl(pointName = "ConfigQuery")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.OPEN_API)
    public Result<ConfigQueryResponse> getConfig(ConfigFormV3 configForm)
            throws NacosApiException, UnsupportedEncodingException {
        configForm.validate();
        configForm.setNamespaceId(NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId()));
        RequestContext requestContext = RequestContextHolder.getContext();
        String sourceIp = requestContext.getBasicContext().getAddressContext().getSourceIp();
        ConfigQueryChainRequest chainRequest = buildQueryChainRequest(configForm, sourceIp);
        ConfigQueryChainResponse chainResponse = configQueryChainService.handle(chainRequest);
        if (Objects.isNull(chainResponse.getContent())) {
            traceQuery(configForm, chainResponse, requestContext, sourceIp, ConfigTraceService.PULL_TYPE_NOTFOUND);
            return Result.failure(ErrorCode.RESOURCE_NOT_FOUND);
        }
        traceQuery(configForm, chainResponse, requestContext, sourceIp, ConfigTraceService.PULL_TYPE_OK);
        return Result.success(transferToResult(chainResponse));
    }
    
    private ConfigQueryChainRequest buildQueryChainRequest(ConfigFormV3 configForm, String sourceIp) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setTenant(configForm.getNamespaceId());
        request.setGroup(configForm.getGroup());
        request.setDataId(configForm.getDataId());
        Map<String, String> appLabels = new HashMap<>(4);
        appLabels.put(BetaGrayRule.CLIENT_IP_LABEL, sourceIp);
        request.setAppLabels(appLabels);
        return request;
    }
    
    private ConfigQueryResponse transferToResult(ConfigQueryChainResponse chainResponse)
            throws UnsupportedEncodingException {
        ConfigQueryResponse result = new ConfigQueryResponse();
        result.setMd5(chainResponse.getMd5());
        result.setEncryptedDataKey(chainResponse.getEncryptedDataKey());
        result.setContent(chainResponse.getContent());
        result.setContentType(chainResponse.getConfigType());
        result.setLastModified(chainResponse.getLastModified());
        // Check if there is a matched gray rule
        if (chainResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY) {
            if (BetaGrayRule.TYPE_BETA.equals(chainResponse.getMatchedGray().getGrayRule().getType())) {
                result.setBeta(true);
            } else if (TagGrayRule.TYPE_TAG.equals(chainResponse.getMatchedGray().getGrayRule().getType())) {
                result.setTag(URLEncoder.encode(chainResponse.getMatchedGray().getRawGrayRule(), ENCODE_UTF8));
            }
        }
        return result;
    }
    
    private void traceQuery(ConfigFormV3 configForm, ConfigQueryChainResponse chainResponse,
            RequestContext requestContext, String sourceIp, String pullType) {
        final long delayed = System.currentTimeMillis() - chainResponse.getLastModified();
        ConfigTraceService.logPullEvent(configForm.getDataId(), configForm.getGroup(), configForm.getNamespaceId(),
                requestContext.getBasicContext().getApp(), chainResponse.getLastModified(),
                resolvePullEventType(chainResponse), pullType, delayed, sourceIp, false, "http");
    }
    
    private String resolvePullEventType(ConfigQueryChainResponse chainResponse) {
        if (Objects.requireNonNull(chainResponse.getStatus())
                == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY) {
            ConfigCacheGray matchedGray = chainResponse.getMatchedGray();
            if (matchedGray != null) {
                return ConfigTraceService.PULL_EVENT + "-" + matchedGray.getGrayName();
            } else {
                return ConfigTraceService.PULL_EVENT;
            }
        }
        return ConfigTraceService.PULL_EVENT;
    }
}
