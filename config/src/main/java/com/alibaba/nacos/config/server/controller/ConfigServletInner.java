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

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ConfigContentWriter;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.Protocol;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import org.springframework.stereotype.Service;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

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
    
    private final LongPollingService longPollingService;
    
    private final PersistService persistService;
    
    public ConfigServletInner(LongPollingService longPollingService, PersistService persistService) {
        this.longPollingService = longPollingService;
        this.persistService = persistService;
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
     * Execute to get config API.
     */
    public String doGetConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag, String isNotify, String clientIp) throws IOException, ServletException {
        
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        final String vipServerTag = request.getHeader(com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG);
    
        final String requestIpApp = RequestUtil.getAppName(request);
        final String requestIp = RequestUtil.getRemoteIp(request);
        final int lockResult = tryConfigReadLock(groupKey);
        
        // lockResult == 0 No data and failed
        if (lockResult == 0) {
            ConfigContentWriter writer = ConfigContentWriter.Builder.build(null, dataId, group, tenant, tag,
                    vipServerTag, clientIp, requestIp, requestIpApp, isNotify);
            // 404 not found
            return writer.write404(response);
        }
        
        // lockResult < 0  Negative number - lock failed。
        if (lockResult < 0) {
            PULL_LOG.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);
    
            ConfigContentWriter writer = ConfigContentWriter.Builder.build(null, dataId, group, tenant, tag,
                    vipServerTag, clientIp, requestIp, requestIpApp, isNotify);
            // 409 conflict
            return writer.write409(response);
        }
        
        // LockResult > 0 means cacheItem is not null and other thread can`t delete this cacheItem
        try {
            CacheItem cacheItem = ConfigCacheService.getContentCache(groupKey);
            // build contentWriter
            ConfigContentWriter contentWriter = ConfigContentWriter.Builder.build(cacheItem, dataId, group, tenant, tag,
                    vipServerTag, clientIp, requestIp, requestIpApp, isNotify);
            // write
            String responseRet = contentWriter.write(response, persistService);
        
            LogUtil.PULL_CHECK_LOG.warn("{}|{}|{}|{}", groupKey, requestIp, contentWriter.getMd5(),
                    TimeUtils.getCurrentTimeStr());
        
            final long delayed = System.currentTimeMillis() - contentWriter.getLastModified();
        
            // TODO distinguish pull-get && push-get
            /*
             Otherwise, delayed cannot be used as the basis of push delay directly,
             because the delayed value of active get requests is very large.
             */
            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, contentWriter.getLastModified(),
                    ConfigTraceService.PULL_EVENT_OK, delayed, requestIp,
                    contentWriter.isNotify() && contentWriter.isSli());
            
            return responseRet;
        } finally {
            releaseConfigReadLock(groupKey);
        }
    }
    
    private static void releaseConfigReadLock(String groupKey) {
        ConfigCacheService.releaseReadLock(groupKey);
    }
    
    /**
     * Try to add read lock.
     *
     * @param groupKey groupKey string value.
     * @return 0 - No data and failed. Positive number - lock succeeded. Negative number - lock failed。
     */
    private static int tryConfigReadLock(String groupKey) {
        
        // Lock failed by default.
        int lockResult = -1;
        
        // Try to get lock times, max value: 10;
        for (int i = TRY_GET_LOCK_TIMES; i >= 0; --i) {
            lockResult = ConfigCacheService.tryReadLock(groupKey);
            
            // The data is non-existent.
            if (0 == lockResult) {
                break;
            }
            
            // Success
            if (lockResult > 0) {
                break;
            }
            
            // Retry.
            if (i > 0) {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                    LogUtil.PULL_CHECK_LOG.error("An Exception occurred while thread sleep", e);
                }
            }
        }
        
        return lockResult;
    }
}
