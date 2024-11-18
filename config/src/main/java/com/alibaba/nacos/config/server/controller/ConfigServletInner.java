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
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;
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
    
    public ConfigServletInner(LongPollingService longPollingService) {
        this.longPollingService = longPollingService;
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
     * Execute to get config [API V1].
     */
    public String doGetConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag, String isNotify, String clientIp) throws IOException, ServletException {
        return doGetConfig(request, response, dataId, group, tenant, tag, isNotify, clientIp, false);
    }
    
    /**
     * Execute to get config [API V1] or [API V2].
     */
    public String doGetConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag, String isNotify, String clientIp, boolean isV2) throws IOException {
        
        boolean notify = StringUtils.isNotBlank(isNotify) && Boolean.parseBoolean(isNotify);
        
        String acceptCharset = ENCODE_UTF8;
        
        if (isV2) {
            response.setHeader(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        }
        
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String autoTag = request.getHeader(com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG);
        
        String requestIpApp = RequestUtil.getAppName(request);
        int lockResult = ConfigCacheService.tryConfigReadLock(groupKey);
        CacheItem cacheItem = ConfigCacheService.getContentCache(groupKey);
        
        final String requestIp = RequestUtil.getRemoteIp(request);
        if (lockResult > 0 && cacheItem != null) {
            try {
                long lastModified;
                
                final String configType =
                        (null != cacheItem.getType()) ? cacheItem.getType() : FileTypeEnum.TEXT.getFileType();
                response.setHeader(com.alibaba.nacos.api.common.Constants.CONFIG_TYPE, configType);
                FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(configType);
                String contentTypeHeader = fileTypeEnum.getContentType();
                response.setHeader(HttpHeaderConsts.CONTENT_TYPE,
                        isV2 ? MediaType.APPLICATION_JSON : contentTypeHeader);
                
                ConfigCacheGray matchedGray = null;
                Map<String, String> appLabels = new HashMap(4);
                appLabels.put(BetaGrayRule.CLIENT_IP_LABEL, clientIp);
                boolean specificTag = StringUtils.isNotBlank(tag);
                
                if (specificTag) {
                    appLabels.put(TagGrayRule.VIP_SERVER_TAG_LABEL, tag);
                } else if (StringUtils.isNotBlank(autoTag)) {
                    appLabels.put(TagGrayRule.VIP_SERVER_TAG_LABEL, autoTag);
                }
                
                if (cacheItem.getSortConfigGrays() != null && !cacheItem.getSortConfigGrays().isEmpty()) {
                    for (ConfigCacheGray configCacheGray : cacheItem.getSortConfigGrays()) {
                        if (configCacheGray.match(appLabels)) {
                            matchedGray = configCacheGray;
                            break;
                        }
                    }
                }
                
                String pullEvent;
                String content;
                String md5;
                String encryptedDataKey;
                
                if (matchedGray != null) {
                    md5 = matchedGray.getMd5(acceptCharset);
                    lastModified = matchedGray.getLastModifiedTs();
                    encryptedDataKey = matchedGray.getEncryptedDataKey();
                    content = ConfigDiskServiceFactory.getInstance()
                            .getGrayContent(dataId, group, tenant, matchedGray.getGrayName());
                    pullEvent = ConfigTraceService.PULL_EVENT + "-" + matchedGray.getGrayName();
                    if (BetaGrayRule.TYPE_BETA.equals(matchedGray.getGrayName())) {
                        response.setHeader("isBeta", "true");
                    }
                    if (TagGrayRule.TYPE_TAG.equals(matchedGray.getGrayRule().getType())) {
                        response.setHeader(com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG,
                                URLEncoder.encode(matchedGray.getGrayRule().getRawGrayRuleExp(),
                                        StandardCharsets.UTF_8.displayName()));
                    }
                } else if (specificTag) {
                    //specific tag is not found
                    md5 = null;
                    lastModified = 0L;
                    encryptedDataKey = null;
                    content = null;
                    pullEvent = ConfigTraceService.PULL_EVENT + "-" + TagGrayRule.TYPE_TAG + "-" + tag;
                    response.setHeader(com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG,
                            URLEncoder.encode(tag, StandardCharsets.UTF_8.displayName()));
                } else {
                    md5 = cacheItem.getConfigCache().getMd5(acceptCharset);
                    lastModified = cacheItem.getConfigCache().getLastModifiedTs();
                    encryptedDataKey = cacheItem.getConfigCache().getEncryptedDataKey();
                    content = ConfigDiskServiceFactory.getInstance().getContent(dataId, group, tenant);
                    pullEvent = ConfigTraceService.PULL_EVENT;
                }
                
                if (content == null) {
                    ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, pullEvent,
                            ConfigTraceService.PULL_TYPE_NOTFOUND, -1, requestIp, notify, "http");
                    return get404Result(response, isV2);
                    
                }
                response.setHeader(Constants.CONTENT_MD5, md5);
                
                // Disable cache.
                response.setHeader("Pragma", "no-cache");
                response.setDateHeader("Expires", 0);
                response.setHeader("Cache-Control", "no-cache,no-store");
                response.setDateHeader("Last-Modified", lastModified);
                if (encryptedDataKey != null) {
                    response.setHeader("Encrypted-Data-Key", encryptedDataKey);
                }
                PrintWriter out;
                Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey, content);
                String decryptContent = pair.getSecond();
                out = response.getWriter();
                if (isV2) {
                    out.print(JacksonUtils.toJson(Result.success(decryptContent)));
                } else {
                    out.print(decryptContent);
                }
                
                out.flush();
                out.close();
                
                LogUtil.PULL_CHECK_LOG.warn("{}|{}|{}|{}", groupKey, requestIp, md5, TimeUtils.getCurrentTimeStr());
                
                final long delayed = notify ? -1 : System.currentTimeMillis() - lastModified;
                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, lastModified, pullEvent,
                        ConfigTraceService.PULL_TYPE_OK, delayed, clientIp, notify, "http");
            } finally {
                ConfigCacheService.releaseReadLock(groupKey);
            }
        } else if (lockResult == 0 || cacheItem == null) {
            
            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, ConfigTraceService.PULL_EVENT,
                    ConfigTraceService.PULL_TYPE_NOTFOUND, -1, requestIp, notify, "http");
            return get404Result(response, isV2);
            
        } else {
            
            PULL_LOG.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);
            return get409Result(response, isV2);
        }
        
        return HttpServletResponse.SC_OK + "";
    }
    
    private String get404Result(HttpServletResponse response, boolean isV2) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        PrintWriter writer = response.getWriter();
        if (isV2) {
            writer.println(JacksonUtils.toJson(Result.failure(ErrorCode.RESOURCE_NOT_FOUND, "config data not exist")));
        } else {
            writer.println("config data not exist");
        }
        return HttpServletResponse.SC_NOT_FOUND + "";
    }
    
    private String get409Result(HttpServletResponse response, boolean isV2) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        PrintWriter writer = response.getWriter();
        if (isV2) {
            writer.println(JacksonUtils.toJson(Result.failure(ErrorCode.RESOURCE_CONFLICT,
                    "requested file is being modified, please try later.")));
        } else {
            writer.println("requested file is being modified, please try later.");
        }
        return HttpServletResponse.SC_CONFLICT + "";
    }
}
