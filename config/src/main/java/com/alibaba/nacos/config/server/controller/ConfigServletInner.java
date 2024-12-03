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
import com.alibaba.nacos.config.server.service.query.ConfigChainRequestExtractorService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.query.enums.ResponseCode;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.Protocol;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
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
import static com.alibaba.nacos.config.server.utils.LogUtil.PULL_LOG;

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
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String requestIpApp = RequestUtil.getAppName(request);
        
        ConfigQueryChainRequest chainRequest = ConfigChainRequestExtractorService.getExtractor().extract(request);
        ConfigQueryChainResponse chainResponse = configQueryChainService.handle(chainRequest);
        
        if (ResponseCode.FAIL.getCode() == chainResponse.getResultCode()) {
            throw new NacosConfigException(chainResponse.getMessage());
        }
        
        String pullEvent = resolvePullEventType(chainResponse, tag);
        
        switch (chainResponse.getStatus()) {
            case CONFIG_NOT_FOUND:
                return handlerConfigNotFound(response, dataId, group, tenant, requestIpApp, clientIp, notify,
                        pullEvent, apiVersion);
            case CONFIG_QUERY_CONFLICT:
                return handlerConfigConflict(response, apiVersion, clientIp, groupKey);
            default:
                return handleResponse(response, chainResponse, dataId, group, tenant, tag, requestIpApp,
                        clientIp, notify, pullEvent, apiVersion);
        }
    }
    
    private String handlerConfigNotFound(HttpServletResponse response, String dataId, String group, String tenant, String requestIpApp,
            String clientIp, boolean notify, String pullEvent, ApiVersionEnum apiVersion) throws IOException {
        ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, pullEvent,
                ConfigTraceService.PULL_TYPE_NOTFOUND, -1, clientIp, notify, "http");
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return writeResponse(response, apiVersion, Result.failure(ErrorCode.RESOURCE_NOT_FOUND, "config data not exist"));
    }
    
    private String handlerConfigConflict(HttpServletResponse response, ApiVersionEnum apiVersion, String clientIp,
            String groupKey) throws IOException {
        PULL_LOG.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        return writeResponse(response, apiVersion,
                Result.failure(ErrorCode.RESOURCE_CONFLICT, "requested file is being modified, please try later."));
    }
    
    private String handleResponse(HttpServletResponse response, ConfigQueryChainResponse chainResponse, String dataId,
            String group, String tenant, String tag, String requestIpApp, String clientIp,
            boolean notify, String pullEvent, ApiVersionEnum apiVersion) throws IOException {
        if (apiVersion == ApiVersionEnum.V1) {
            return handleResponseForVersion(response, chainResponse, dataId, group, tenant, tag, requestIpApp,
                    clientIp, notify, pullEvent, ApiVersionEnum.V1);
        } else {
            return handleResponseForVersion(response, chainResponse, dataId, group, tenant, tag, requestIpApp,
                    clientIp, notify, pullEvent, ApiVersionEnum.V2);
        }
    }
    
    private String handleResponseForVersion(HttpServletResponse response, ConfigQueryChainResponse chainResponse,
            String dataId, String group, String tenant, String tag, String requestIpApp, String clientIp, boolean notify,
            String pullEvent, ApiVersionEnum apiVersion) throws IOException {
        if (chainResponse.getContent() == null) {
            return handlerConfigNotFound(response, dataId, group, tenant, requestIpApp, clientIp, notify, pullEvent, apiVersion);
        }
        
        setResponseHead(response, chainResponse, tag, apiVersion);
        writeContent(response, chainResponse, dataId, apiVersion);
        
        LogUtil.PULL_CHECK_LOG.warn("{}|{}|{}|{}", group, requestIpApp, chainResponse.getMd5(), TimeUtils.getCurrentTimeStr());
        final long delayed = notify ? -1 : System.currentTimeMillis() - chainResponse.getLastModified();
        ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, chainResponse.getLastModified(), pullEvent,
                ConfigTraceService.PULL_TYPE_OK, delayed, clientIp, notify, "http");
        
        return HttpServletResponse.SC_OK + "";
    }

    private void writeContent(HttpServletResponse response, ConfigQueryChainResponse chainResponse, String dataId,
            ApiVersionEnum apiVersion) throws IOException {
        PrintWriter out = response.getWriter();
        try {
            Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, chainResponse.getEncryptedDataKey(),
                    chainResponse.getContent());
            String decryptContent = pair.getSecond();
            if (ApiVersionEnum.V2 == apiVersion) {
                out.print(JacksonUtils.toJson(Result.success(decryptContent)));
            } else {
                out.print(decryptContent);
            }
        } finally {
            out.flush();
            out.close();
        }
    }
    
    private String writeResponse(HttpServletResponse response, ApiVersionEnum apiVersion, Result<String> result)
            throws IOException {
        PrintWriter writer = response.getWriter();
        if (ApiVersionEnum.V2 == apiVersion) {
            writer.println(JacksonUtils.toJson(result));
        } else {
            writer.println(result.getData());
        }
        return response.getStatus() + "";
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
    
    private void setResponseHead(HttpServletResponse response, ConfigQueryChainResponse chainResponse, String tag, ApiVersionEnum version) {
        String contentType = chainResponse.getContentType() != null ? chainResponse.getContentType() : FileTypeEnum.TEXT.getFileType();
        FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(contentType);
        String contentTypeHeader = fileTypeEnum.getContentType();
        
        response.setHeader(CONFIG_TYPE, contentType);
        response.setHeader(HttpHeaderConsts.CONTENT_TYPE,
                ApiVersionEnum.V2 == version ? MediaType.APPLICATION_JSON : contentTypeHeader);
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
                    response.setHeader(TagGrayRule.TYPE_TAG, URLEncoder.encode(chainResponse.getMatchedGray().getGrayRule().getRawGrayRuleExp(),
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
