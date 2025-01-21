/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.query.ConfigChainRequestExtractorService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.ConfigRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;
import static com.alibaba.nacos.config.server.utils.LogUtil.PULL_LOG;
import static com.alibaba.nacos.config.server.utils.RequestUtil.CLIENT_APPNAME_HEADER;

/**
 * ConfigQueryRequestHandler.
 *
 * @author liuzunfei
 * @version $Id: ConfigQueryRequestHandler.java, v 0.1 2020年07月14日 9:54 AM liuzunfei Exp $
 */
@Component
public class ConfigQueryRequestHandler extends RequestHandler<ConfigQueryRequest, ConfigQueryResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigQueryRequestHandler.class);
    
    private final ConfigQueryChainService configQueryChainService;
    
    public ConfigQueryRequestHandler(ConfigQueryChainService configQueryChainService) {
        this.configQueryChainService = configQueryChainService;
    }
    
    @Override
    @TpsControl(pointName = "ConfigQuery")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @ExtractorManager.Extractor(rpcExtractor = ConfigRequestParamExtractor.class)
    public ConfigQueryResponse handle(ConfigQueryRequest request, RequestMeta meta) throws NacosException {
        try {
            String dataId = request.getDataId();
            String group = request.getGroup();
            String tenant = request.getTenant();
            String groupKey = GroupKey2.getKey(dataId, group, tenant);
            boolean notify = request.isNotify();
            
            String requestIpApp = meta.getLabels().get(CLIENT_APPNAME_HEADER);
            String clientIp = meta.getClientIp();
            
            ConfigQueryChainRequest chainRequest = ConfigChainRequestExtractorService.getExtractor().extract(request, meta);
            ConfigQueryChainResponse chainResponse = configQueryChainService.handle(chainRequest);
            
            if (ResponseCode.FAIL.getCode() == chainResponse.getResultCode()) {
                return ConfigQueryResponse.buildFailResponse(ResponseCode.FAIL.getCode(), chainResponse.getMessage());
            }
            
            if (chainResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
                return handlerConfigNotFound(request.getDataId(), request.getGroup(), request.getTenant(), requestIpApp, clientIp, notify);
            }
            
            if (chainResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT) {
                return handlerConfigConflict(clientIp, groupKey);
            }
            
            ConfigQueryResponse response = new ConfigQueryResponse();
            
            // Check if there is a matched gray rule
            if (chainResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY) {
                if (BetaGrayRule.TYPE_BETA.equals(chainResponse.getMatchedGray().getGrayRule().getType())) {
                    response.setBeta(true);
                } else if (TagGrayRule.TYPE_TAG.equals(chainResponse.getMatchedGray().getGrayRule().getType())) {
                    response.setTag(URLEncoder.encode(chainResponse.getMatchedGray().getRawGrayRule(), ENCODE_UTF8));
                }
            }
            
            // Check if there is a special tag
            if (chainResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.SPECIAL_TAG_CONFIG_NOT_FOUND) {
                response.setTag(request.getTag());
            }
            
            response.setMd5(chainResponse.getMd5());
            response.setEncryptedDataKey(chainResponse.getEncryptedDataKey());
            response.setContent(chainResponse.getContent());
            response.setContentType(chainResponse.getConfigType());
            response.setLastModified(chainResponse.getLastModified());
            
            String pullType = ConfigTraceService.PULL_TYPE_OK;
            if (chainResponse.getContent() == null) {
                pullType = ConfigTraceService.PULL_TYPE_NOTFOUND;
                response.setErrorInfo(ConfigQueryResponse.CONFIG_NOT_FOUND, "config data not exist");
            } else {
                response.setResultCode(ResponseCode.SUCCESS.getCode());
            }
            
            String pullEvent = resolvePullEventType(chainResponse, request.getTag());
            LogUtil.PULL_CHECK_LOG.warn("{}|{}|{}|{}", groupKey, clientIp, response.getMd5(), TimeUtils.getCurrentTimeStr());
            final long delayed = System.currentTimeMillis() - response.getLastModified();
            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, response.getLastModified(), pullEvent, pullType,
                    delayed, clientIp, notify, "grpc");
            
            return response;
            
        } catch (Exception e) {
            LOGGER.error("Failed to handle grpc configuration query", e);
            return ConfigQueryResponse.buildFailResponse(ResponseCode.FAIL.getCode(), e.getMessage());
        }
        
    }
    
    private ConfigQueryResponse handlerConfigConflict(String clientIp, String groupKey) {
        ConfigQueryResponse response = new ConfigQueryResponse();
        
        PULL_LOG.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);
        response.setErrorInfo(ConfigQueryResponse.CONFIG_QUERY_CONFLICT,
                "requested file is being modified, please try later.");
        
        return response;
    }
    
    private ConfigQueryResponse handlerConfigNotFound(String dataId, String group, String tenant, String requestIpApp,
            String clientIp, boolean notify) {
        //CacheItem No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
        ConfigQueryResponse response = new ConfigQueryResponse();
        ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, ConfigTraceService.PULL_EVENT,
                ConfigTraceService.PULL_TYPE_NOTFOUND, -1, clientIp, notify, "grpc");
        response.setErrorInfo(ConfigQueryResponse.CONFIG_NOT_FOUND, "config data not exist");
        
        return response;
        
    }
    
    private String resolvePullEventType(ConfigQueryChainResponse chainResponse, String tag) {
        switch (chainResponse.getStatus()) {
            case CONFIG_FOUND_GRAY:
                ConfigCacheGray matchedGray = chainResponse.getMatchedGray();
                if (matchedGray != null) {
                    return ConfigTraceService.PULL_EVENT + "-" + matchedGray.getGrayName();
                } else {
                    return ConfigTraceService.PULL_EVENT;
                }
            case SPECIAL_TAG_CONFIG_NOT_FOUND:
                return ConfigTraceService.PULL_EVENT + "-" + TagGrayRule.TYPE_TAG + "-" + tag;
            default:
                return ConfigTraceService.PULL_EVENT;
        }
    }
}
