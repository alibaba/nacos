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

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.utils.DiskUtil;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.Protocol;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private LongPollingService longPollingService;

    @Autowired
    private PersistService persistService;

    private static final int TRY_GET_LOCK_TIMES = 9;

    private static final int START_LONG_POLLING_VERSION_NUM = 204;

    /**
     * 轮询接口.
     */
    public String doPollingConfig(HttpServletRequest request, HttpServletResponse response,
            Map<String, String> clientMd5Map, int probeRequestSize) throws IOException {

        // Long polling.
        /**
         * 长轮询监听
         */
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

        // Befor 2.0.4 version, return value is put into header.
        if (versionNum < START_LONG_POLLING_VERSION_NUM) {
            response.addHeader(Constants.PROBE_MODIFY_RESPONSE, oldResult);
            response.addHeader(Constants.PROBE_MODIFY_RESPONSE_NEW, newResult);
        } else {
            request.setAttribute("content", newResult);
        }

        Loggers.AUTH.info("new content:" + newResult);

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
            String tenant, String tag, String clientIp) throws IOException, ServletException {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String autoTag = request.getHeader("Vipserver-Tag");
        String requestIpApp = RequestUtil.getAppName(request);
        /**
         * 加读锁
         */
        int lockResult = tryConfigReadLock(groupKey);

        final String requestIp = RequestUtil.getRemoteIp(request);
        boolean isBeta = false;
        /**
         * 加锁成功
         */
        if (lockResult > 0) {
            FileInputStream fis = null;
            try {
                String md5 = Constants.NULL;
                long lastModified = 0L;
                /**
                 * 在cache中获取groupKey对应的CacheItem
                 */
                CacheItem cacheItem = ConfigCacheService.getContentCache(groupKey);
                if (cacheItem != null) {
                    if (cacheItem.isBeta()) {
                        /**
                         * 访问的客户端是beta中对应的ip
                         */
                        if (cacheItem.getIps4Beta().contains(clientIp)) {
                            isBeta = true;
                        }
                    }

                    final String configType =
                            (null != cacheItem.getType()) ? cacheItem.getType() : FileTypeEnum.TEXT.getFileType();
                    response.setHeader("Config-Type", configType);

                    String contentTypeHeader;
                    try {
                        contentTypeHeader = FileTypeEnum.valueOf(configType.toUpperCase()).getContentType();
                    } catch (IllegalArgumentException ex) {
                        contentTypeHeader = FileTypeEnum.TEXT.getContentType();
                    }
                    response.setHeader(HttpHeaderConsts.CONTENT_TYPE, contentTypeHeader);
                }
                File file = null;
                ConfigInfoBase configInfoBase = null;
                PrintWriter out = null;
                /**
                 * beta客户端访问
                 */
                if (isBeta) {
                    md5 = cacheItem.getMd54Beta();
                    lastModified = cacheItem.getLastModifiedTs4Beta();
                    if (PropertyUtil.isDirectRead()) {
                        /**
                         * 单机   并且没有使用mysql    则获取config_info_beta对应的值
                         */
                        configInfoBase = persistService.findConfigInfo4Beta(dataId, group, tenant);
                    } else {
                        /**
                         * 返回服务端beta缓存文件的路径
                         */
                        file = DiskUtil.targetBetaFile(dataId, group, tenant);
                    }
                    response.setHeader("isBeta", "true");
                } else {
                    /**
                     * 没有tag
                     */
                    if (StringUtils.isBlank(tag)) {
                        /**
                         * 使用autoTag
                         */
                        if (isUseTag(cacheItem, autoTag)) {
                            if (cacheItem != null) {
                                if (cacheItem.tagMd5 != null) {
                                    md5 = cacheItem.tagMd5.get(autoTag);
                                }
                                if (cacheItem.tagLastModifiedTs != null) {
                                    lastModified = cacheItem.tagLastModifiedTs.get(autoTag);
                                }
                            }
                            if (PropertyUtil.isDirectRead()) {
                                /**
                                 * 单机   并且没有使用mysql    则获取config_info_tag对应的值
                                 */
                                configInfoBase = persistService.findConfigInfo4Tag(dataId, group, tenant, autoTag);
                            } else {
                                /**
                                 * 返回服务端Tag缓存文件的路径
                                 */
                                file = DiskUtil.targetTagFile(dataId, group, tenant, autoTag);
                            }

                            response.setHeader("Vipserver-Tag",
                                    URLEncoder.encode(autoTag, StandardCharsets.UTF_8.displayName()));
                        } else {
                            /**
                             * 不使用autoTag
                             */
                            md5 = cacheItem.getMd5();
                            lastModified = cacheItem.getLastModifiedTs();
                            if (PropertyUtil.isDirectRead()) {
                                /**
                                 * 单机   并且没有使用mysql    则获取config_info对应的值
                                 */
                                configInfoBase = persistService.findConfigInfo(dataId, group, tenant);
                            } else {
                                /**
                                 * 返回服务端缓存文件的路径
                                 */
                                file = DiskUtil.targetFile(dataId, group, tenant);
                            }
                            /**
                             * 配置不存在
                             */
                            if (configInfoBase == null && fileNotExist(file)) {
                                // FIXME CacheItem
                                // No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
                                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                                        ConfigTraceService.PULL_EVENT_NOTFOUND, -1, requestIp);

                                // pullLog.info("[client-get] clientIp={}, {},
                                // no data",
                                // new Object[]{clientIp, groupKey});

                                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                                response.getWriter().println("config data not exist");
                                return HttpServletResponse.SC_NOT_FOUND + "";
                            }
                        }
                    } else {
                        /**
                         * 有tag
                         */
                        if (cacheItem != null) {
                            if (cacheItem.tagMd5 != null) {
                                md5 = cacheItem.tagMd5.get(tag);
                            }
                            if (cacheItem.tagLastModifiedTs != null) {
                                Long lm = cacheItem.tagLastModifiedTs.get(tag);
                                if (lm != null) {
                                    lastModified = lm;
                                }
                            }
                        }
                        if (PropertyUtil.isDirectRead()) {
                            /**
                             * 单机   并且没有使用mysql    则获取config_info_tag对应的值
                             */
                            configInfoBase = persistService.findConfigInfo4Tag(dataId, group, tenant, tag);
                        } else {
                            /**
                             * 返回服务端Tag缓存文件的路径
                             */
                            file = DiskUtil.targetTagFile(dataId, group, tenant, tag);
                        }

                        /**
                         * 配置不存在
                         */
                        if (configInfoBase == null && fileNotExist(file)) {
                            // FIXME CacheItem
                            // No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
                            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                                    ConfigTraceService.PULL_EVENT_NOTFOUND, -1, requestIp);

                            // pullLog.info("[client-get] clientIp={}, {},
                            // no data",
                            // new Object[]{clientIp, groupKey});

                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            response.getWriter().println("config data not exist");
                            return HttpServletResponse.SC_NOT_FOUND + "";
                        }
                    }
                }

                response.setHeader(Constants.CONTENT_MD5, md5);

                // Disable cache.
                response.setHeader("Pragma", "no-cache");
                response.setDateHeader("Expires", 0);
                response.setHeader("Cache-Control", "no-cache,no-store");
                if (PropertyUtil.isDirectRead()) {
                    response.setDateHeader("Last-Modified", lastModified);
                } else {
                    fis = new FileInputStream(file);
                    response.setDateHeader("Last-Modified", file.lastModified());
                }

                /**
                 * 将配置信息写入返回对象
                 */
                if (PropertyUtil.isDirectRead()) {
                    out = response.getWriter();
                    out.print(configInfoBase.getContent());
                    out.flush();
                    out.close();
                } else {
                    fis.getChannel()
                            .transferTo(0L, fis.getChannel().size(), Channels.newChannel(response.getOutputStream()));
                }

                LogUtil.PULL_CHECK_LOG.warn("{}|{}|{}|{}", groupKey, requestIp, md5, TimeUtils.getCurrentTimeStr());

                final long delayed = System.currentTimeMillis() - lastModified;

                // TODO distinguish pull-get && push-get
                /*
                 Otherwise, delayed cannot be used as the basis of push delay directly,
                 because the delayed value of active get requests is very large.
                 */
                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, lastModified,
                        ConfigTraceService.PULL_EVENT_OK, delayed, requestIp);

            } finally {
                /**
                 * 释放读锁
                 */
                releaseConfigReadLock(groupKey);
                if (null != fis) {
                    fis.close();
                }
            }
        } else if (lockResult == 0) {
            /**
             * 数据不存在
             */
            // FIXME CacheItem No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
            ConfigTraceService
                    .logPullEvent(dataId, group, tenant, requestIpApp, -1, ConfigTraceService.PULL_EVENT_NOTFOUND, -1,
                            requestIp);

            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().println("config data not exist");
            return HttpServletResponse.SC_NOT_FOUND + "";

        } else {
            /**
             * 加锁失败   有进程正在写入
             */
            PULL_LOG.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);

            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().println("requested file is being modified, please try later.");
            return HttpServletResponse.SC_CONFLICT + "";

        }

        return HttpServletResponse.SC_OK + "";
    }

    private static void releaseConfigReadLock(String groupKey) {
        ConfigCacheService.releaseReadLock(groupKey);
    }
    /**
     * 加读锁
     * @param groupKey
     * @return
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

    private static boolean isUseTag(CacheItem cacheItem, String tag) {
        if (cacheItem != null && cacheItem.tagMd5 != null && cacheItem.tagMd5.size() > 0) {
            return StringUtils.isNotBlank(tag) && cacheItem.tagMd5.containsKey(tag);
        }
        return false;
    }

    private static boolean fileNotExist(File file) {
        return file == null || !file.exists();
    }

}
