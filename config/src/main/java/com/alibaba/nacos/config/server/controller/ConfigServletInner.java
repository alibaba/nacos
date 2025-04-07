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

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.ApiVersionEnum;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.query.ConfigChainRequestExtractorService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.enums.ResponseCode;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.Protocol;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.CONFIG_TYPE;
import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;
import static com.alibaba.nacos.config.server.constant.Constants.CONTENT_MD5;

/**
 * ConfigServlet inner for aop.
 *
 * @author Nacos
 */
@Service
public class ConfigServletInner {
    
    private static final int TRY_GET_LOCK_TIMES = 9;
    
    private static final int START_LONG_POLLING_VERSION_NUM = 204;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigServletInner.class);
    
    private final LongPollingService longPollingService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    public ConfigServletInner(LongPollingService longPollingService, ConfigQueryChainService configQueryChainService) {
        this.longPollingService = longPollingService;
        this.configQueryChainService = configQueryChainService;
    }
    
    private static String getDecryptContent(ConfigQueryChainResponse chainResponse, String dataId) {
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, chainResponse.getEncryptedDataKey(),
                chainResponse.getContent());
        return pair.getSecond();
    }
    
    /**
     * long polling the config.
     */
    public String doPollingConfig(HttpServletRequest request, HttpServletResponse response,
            Map<String, String> clientMd5Map, int probeRequestSize) throws IOException {
        
        // Long polling.
        if (LongPollingService.isSupportLongPolling(request)) {
            longPollingService.addLongPollingClient(request, response, clientMd5Map, probeRequestSize);
            return HttpServletResponse.SC_OK + "";
        }
        
        // Compatible with short polling logic.
        List<String> changedGroups = MD5Util.compareMd5(request, response, clientMd5Map);
        
        // Compatible with short polling result.
        String oldResult = MD5Util.compareMd5OldResult(changedGroups);
        String newResult = MD5Util.compareMd5ResultString(changedGroups);
        
        String version = request.getHeader(Constants.CLIENT_VERSION_HEADER);
        if (version == null) {
            version = "2.0.0";
        }
        int versionNum = Protocol.getVersionNumber(version);
        
        // Before 2.0.4 version, return value is put into header.
        if (versionNum < START_LONG_POLLING_VERSION_NUM) {
            response.addHeader(Constants.PROBE_MODIFY_RESPONSE, oldResult);
            response.addHeader(Constants.PROBE_MODIFY_RESPONSE_NEW, newResult);
        } else {
            request.setAttribute("content", newResult);
        }
        
        // Disable cache.
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setHeader("Cache-Control", "no-cache,no-store");
        response.setStatus(HttpServletResponse.SC_OK);
        return HttpServletResponse.SC_OK + "";
    }
    
    /**
     * Execute to get config [API V1] or [API V2].
     */
    public String doGetConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag, String isNotify, String clientIp, ApiVersionEnum apiVersion) throws IOException {
        
        boolean notify = StringUtils.isNotBlank(isNotify) && Boolean.parseBoolean(isNotify);
        String requestIpApp = RequestUtil.getAppName(request);
        
        ConfigQueryChainRequest chainRequest = ConfigChainRequestExtractorService.getExtractor().extract(request);
        ConfigQueryChainResponse chainResponse = configQueryChainService.handle(chainRequest);
        
        if (ResponseCode.FAIL.getCode() == chainResponse.getResultCode()) {
            throw new NacosConfigException(chainResponse.getMessage());
        }
        
        logPullEvent(dataId, group, tenant, requestIpApp, chainResponse, clientIp, notify, tag);
        
        switch (chainResponse.getStatus()) {
            case CONFIG_NOT_FOUND:
            case SPECIAL_TAG_CONFIG_NOT_FOUND:
                return handlerConfigNotFound(response, apiVersion);
            case CONFIG_QUERY_CONFLICT:
                return handlerConfigConflict(response, apiVersion);
            default:
                return handleResponse(response, chainResponse, dataId, group, apiVersion);
        }
    }
    
    private String handlerConfigNotFound(HttpServletResponse response, ApiVersionEnum apiVersion) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        if (apiVersion == ApiVersionEnum.V1) {
            return writeResponseForV1(response, Result.failure(ErrorCode.RESOURCE_NOT_FOUND, "config data not exist"));
        } else {
            return writeResponseForV2(response, Result.failure(ErrorCode.RESOURCE_NOT_FOUND, "config data not exist"));
        }
    }
    
    private String handlerConfigConflict(HttpServletResponse response, ApiVersionEnum apiVersion) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        if (apiVersion == ApiVersionEnum.V1) {
            return writeResponseForV1(response,
                    Result.failure(ErrorCode.RESOURCE_CONFLICT, "requested file is being modified, please try later."));
        } else {
            return writeResponseForV2(response,
                    Result.failure(ErrorCode.RESOURCE_CONFLICT, "requested file is being modified, please try later."));
        }
    }
    
    private String handleResponse(HttpServletResponse response, ConfigQueryChainResponse chainResponse, String dataId,
            String group, ApiVersionEnum apiVersion) throws IOException {
        if (apiVersion == ApiVersionEnum.V1) {
            return handleResponseForV1(response, chainResponse, dataId, group);
        } else {
            return handleResponseForV2(response, chainResponse, dataId, group);
        }
    }
    
    private String handleResponseForV1(HttpServletResponse response, ConfigQueryChainResponse chainResponse,
            String dataId, String tag) throws IOException {
        if (chainResponse.getContent() == null) {
            return handlerConfigNotFound(response, ApiVersionEnum.V1);
        }
        
        setCommonResponseHead(response, chainResponse, tag);
        setResponseHeadForV1(response, chainResponse);
        writeContentForV1(response, chainResponse, dataId);
        
        return HttpServletResponse.SC_OK + "";
    }
    
    private String handleResponseForV2(HttpServletResponse response, ConfigQueryChainResponse chainResponse,
            String dataId, String tag) throws IOException {
        if (chainResponse.getContent() == null) {
            return handlerConfigNotFound(response, ApiVersionEnum.V2);
        }
        
        setCommonResponseHead(response, chainResponse, tag);
        setResponseHeadForV2(response);
        writeContentForV2(response, chainResponse, dataId);
        
        return HttpServletResponse.SC_OK + "";
    }
    
    private void setResponseHeadForV1(HttpServletResponse response, ConfigQueryChainResponse chainResponse) {
        String contentType = chainResponse.getContentType();
        if (StringUtils.isBlank(contentType)) {
            contentType = FileTypeEnum.TEXT.getContentType();
        }
        response.setHeader(HttpHeaderConsts.CONTENT_TYPE, contentType);
    }
    
    private void setResponseHeadForV2(HttpServletResponse response) {
        response.setHeader(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_JSON);
    }
    
    private void writeContentForV1(HttpServletResponse response, ConfigQueryChainResponse chainResponse, String dataId)
            throws IOException {
        PrintWriter out = response.getWriter();
        try {
            String decryptContent = getDecryptContent(chainResponse, dataId);
            out.print(decryptContent);
        } finally {
            out.flush();
            out.close();
        }
    }
    
    private void writeContentForV2(HttpServletResponse response, ConfigQueryChainResponse chainResponse, String dataId)
            throws IOException {
        PrintWriter out = response.getWriter();
        try {
            String decryptContent = getDecryptContent(chainResponse, dataId);
            out.print(JacksonUtils.toJson(Result.success(decryptContent)));
        } finally {
            out.flush();
            out.close();
        }
    }
    
    private String writeResponseForV1(HttpServletResponse response, Result<String> result) throws IOException {
        PrintWriter writer = response.getWriter();
        writer.println(result.getData());
        return response.getStatus() + "";
    }
    
    private String writeResponseForV2(HttpServletResponse response, Result<String> result) throws IOException {
        PrintWriter writer = response.getWriter();
        writer.println(JacksonUtils.toJson(result));
        return response.getStatus() + "";
    }
    
    private String resolvePullEvent(ConfigQueryChainResponse chainResponse, String tag) {
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
    
    private void logPullEvent(String dataId, String group, String tenant, String requestIpApp,
            ConfigQueryChainResponse chainResponse, String clientIp, boolean notify, String tag) {
        
        String pullEvent = resolvePullEvent(chainResponse, tag);
        
        ConfigQueryChainResponse.ConfigQueryStatus status = chainResponse.getStatus();
        
        if (status == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT) {
            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, pullEvent,
                    ConfigTraceService.PULL_TYPE_CONFLICT, -1, clientIp, notify, "http");
        } else if (status == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND
                || chainResponse.getContent() == null) {
            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, pullEvent,
                    ConfigTraceService.PULL_TYPE_NOTFOUND, -1, clientIp, notify, "http");
        } else {
            long delayed = System.currentTimeMillis() - chainResponse.getLastModified();
            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, chainResponse.getLastModified(),
                    pullEvent, ConfigTraceService.PULL_TYPE_OK, delayed, clientIp, notify, "http");
        }
    }
    
    private void setCommonResponseHead(HttpServletResponse response, ConfigQueryChainResponse chainResponse,
            String tag) {
        String configType = chainResponse.getConfigType() != null ? chainResponse.getConfigType()
                : FileTypeEnum.TEXT.getFileType();
        
        response.setHeader(CONFIG_TYPE, configType);
        response.setHeader(CONTENT_MD5, chainResponse.getMd5());
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setHeader("Cache-Control", "no-cache,no-store");
        response.setDateHeader("Last-Modified", chainResponse.getLastModified());
        
        if (chainResponse.getEncryptedDataKey() != null) {
            response.setHeader("Encrypted-Data-Key", chainResponse.getEncryptedDataKey());
        }
        
        // Check if there is a matched gray rule
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY == chainResponse.getStatus()) {
            if (BetaGrayRule.TYPE_BETA.equals(chainResponse.getMatchedGray().getGrayRule().getType())) {
                response.setHeader("isBeta", "true");
            } else if (TagGrayRule.TYPE_TAG.equals(chainResponse.getMatchedGray().getGrayRule().getType())) {
                try {
                    response.setHeader(TagGrayRule.TYPE_TAG,
                            URLEncoder.encode(chainResponse.getMatchedGray().getGrayRule().getRawGrayRuleExp(),
                                    StandardCharsets.UTF_8.displayName()));
                } catch (Exception e) {
                    LOGGER.error("Error encoding tag", e);
                }
            }
        }
        
        // Check if there is a special tag
        if (ConfigQueryChainResponse.ConfigQueryStatus.SPECIAL_TAG_CONFIG_NOT_FOUND == chainResponse.getStatus()) {
            try {
                response.setHeader(VIPSERVER_TAG, URLEncoder.encode(tag, StandardCharsets.UTF_8.displayName()));
            } catch (Exception e) {
                LOGGER.error("Error encoding tag", e);
            }
        }
    }
}
