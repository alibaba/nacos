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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;

/**
 * config content writer.
 * @author onewe
 */
public class ConfigContentWriter {
    
    /**
     * config type http header name.
     */
    private static final String CONFIG_TYPE_HEADER = "Config-Type";
    
    /**
     * is beta http header name.
     */
    private static final String IS_BETA_HEADER = "isBeta";
    
    /**
     * config content md5.
     */
    private String md5;
    
    /**
     * config content lastModify.
     */
    private long lastModified;
    
    /**
     * config data id.
     */
    private String dataId;
    
    private String group;
    
    private String tenant;
    
    private String tag;
    
    private String vipServerTag;
    
    private String configType;
    
    private String requestIpApp;
    
    private String requestIp;
    
    private boolean isBeta;
    
    private boolean isSli;
    
    private boolean isNotify;
    
    private boolean isUseVipServerTag;
    
    private boolean isUseNormallyTag;
    
    private ConfigContentWriter(){}
    
    public String getMd5() {
        return md5;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public boolean isSli() {
        return isSli;
    }
    
    public boolean isNotify() {
        return isNotify;
    }
    
    /**
     * write content to http response.
     * @param response http servlet response
     * @param persistService content persist service
     * @return if it found configs it will return 200 ok
     *         if it can't found configs it will return 404 not found
     *         if it can't read configs it will return 409 conflict
     * @throws IOException response write exception
     */
    public String write(HttpServletResponse response, PersistService persistService) throws IOException {
        
        if (PropertyUtil.isDirectRead()) {
            ConfigInfoBase configInfoBase = getConfigInfoBase(persistService);
            return writeToResponseFromConfigInfoBase(response, configInfoBase);
        } else {
            File file = getConfigFile();
            return writeToResponseFromFile(response, file);
        }
    }
    
    /**
     * if it can't found configs,it will return 400 not found.
     * @param response http servlet response
     * @return http status code 404
     * @throws IOException response write exception
     */
    public String write404(HttpServletResponse response) throws IOException {
        ConfigTraceService
                .logPullEvent(dataId, group, tenant, requestIpApp, -1, ConfigTraceService.PULL_EVENT_NOTFOUND, -1,
                        requestIp, isNotify && isSli);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.getWriter().println("config data not exist");
        return HttpServletResponse.SC_NOT_FOUND + "";
    }
    
    /**
     * if it can't read configs, it will return 409 conflict.
     * @param response http servlet response
     * @return http status code 409
     * @throws IOException response write exception
     */
    public String write409(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.getWriter().println("requested file is being modified, please try later.");
        return HttpServletResponse.SC_CONFLICT + "";
    }
    
    private ConfigInfoBase getConfigInfoBase(PersistService persistService) {
        
        if (isBeta) {
            return persistService.findConfigInfo4Beta(dataId, group, tenant);
        }
        
        if (isUseVipServerTag) {
            return persistService.findConfigInfo4Tag(dataId, group, tenant, vipServerTag);
        }
        
        if (isUseNormallyTag) {
            return persistService.findConfigInfo4Tag(dataId, group, tenant, tag);
        }
        
        return persistService.findConfigInfo(dataId, group, tenant);
    }
    
    private File getConfigFile() {
        if (isBeta) {
            return DiskUtil.targetBetaFile(dataId, group, tenant);
        }
        
        if (isUseVipServerTag) {
            return DiskUtil.targetTagFile(dataId, group, tenant, vipServerTag);
        }
        
        if (isUseNormallyTag) {
            return DiskUtil.targetTagFile(dataId, group, tenant, tag);
        }
        
        return DiskUtil.targetFile(dataId, group, tenant);
    }
    
    private void setHeaders(HttpServletResponse response) throws UnsupportedEncodingException {
        FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(configType);
        String contentTypeHeader = fileTypeEnum.getContentType();
        
        response.setHeader(Constants.CONTENT_MD5, md5);
        response.setHeader(CONFIG_TYPE_HEADER, configType);
        response.setHeader(HttpHeaderConsts.CONTENT_TYPE, contentTypeHeader);
        // Disable cache.
        response.setHeader(HttpHeaderConsts.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaderConsts.EXPIRES, 0);
        response.setHeader(HttpHeaderConsts.CACHE_CONTROL, "no-cache,no-store");
        
        if (isBeta) {
            response.setHeader(IS_BETA_HEADER, Boolean.TRUE.toString());
        }
        
        if (isUseVipServerTag) {
            response.setHeader(com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG,
                    URLEncoder.encode(vipServerTag, StandardCharsets.UTF_8.displayName()));
        }
    }
    
    private String writeToResponseFromFile(HttpServletResponse response, File file) throws IOException {
        if (fileNotExist(file)) {
            return write404(response);
        }
        setHeaders(response);
        // set last modify header
        response.setDateHeader(HttpHeaderConsts.LAST_MODIFIED, file.lastModified());
        // write
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.getChannel()
                    .transferTo(0L, fis.getChannel().size(), Channels.newChannel(response.getOutputStream()));
        }
        return HttpServletResponse.SC_OK + "";
        
    }
    
    private String writeToResponseFromConfigInfoBase(HttpServletResponse response, ConfigInfoBase configInfoBase)
            throws IOException {
        if (configInfoBase == null) {
            return write404(response);
        }
        setHeaders(response);
        response.setDateHeader(HttpHeaderConsts.LAST_MODIFIED, lastModified);
        try (PrintWriter out = response.getWriter()) {
            out.print(configInfoBase.getContent());
            out.flush();
        }
        
        return HttpServletResponse.SC_OK + "";
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
    
    public static class Builder {
    
        /**
         * build the config content writer.
         * @param cacheItem the config in the cache
         * @param dataId data id
         * @param group group
         * @param tenant tenant
         * @param tag tag
         * @param vipServerTag vip server tag header value
         * @param clientIp client ip
         * @param requestIp request ip
         * @param requestIpApp request ip app name
         * @param isNotify if it's true, it will notify
         * @return ConfigContentWriter
         */
        public static ConfigContentWriter build(CacheItem cacheItem, String dataId, String group, String tenant,
                String tag, String vipServerTag, String clientIp, String requestIp, String requestIpApp,
                String isNotify) {
            ConfigContentWriter contentWriter = new ConfigContentWriter();
            // init
            init(contentWriter, dataId, group, tenant, tag, vipServerTag, requestIp, requestIpApp,
                    isNotify);
            if (cacheItem == null) {
                return contentWriter;
            }
            // set config type
            contentWriter.configType =
                    (null != cacheItem.getType()) ? cacheItem.getType() : FileTypeEnum.TEXT.getFileType();
            // is Beta?
            if (cacheItem.isBeta() && cacheItem.getIps4Beta().contains(clientIp)) {
                contentWriter.isBeta = true;
                contentWriter.md5 = cacheItem.getMd54Beta();
                contentWriter.lastModified = cacheItem.getLastModifiedTs4Beta();
            } else if (StringUtils.isBlank(tag) && !isUseTag(cacheItem, vipServerTag)) {
                // is not tag
                contentWriter.md5 = cacheItem.getMd5();
                contentWriter.lastModified = cacheItem.getLastModifiedTs();
                contentWriter.isSli = true;
            } else if (StringUtils.isBlank(tag) && isUseTag(cacheItem, vipServerTag)) {
                // use vip server tag
                if (cacheItem.tagMd5 != null) {
                    contentWriter.md5 = cacheItem.tagMd5.get(vipServerTag);
                }
                if (cacheItem.tagLastModifiedTs != null) {
                    contentWriter.lastModified = cacheItem.tagLastModifiedTs.get(vipServerTag);
                }
                contentWriter.vipServerTag = vipServerTag;
                contentWriter.isUseVipServerTag = true;
            } else {
                // use normally tag
                if (cacheItem.tagMd5 != null) {
                    contentWriter.md5 = cacheItem.tagMd5.get(tag);
                }
                if (cacheItem.tagLastModifiedTs != null) {
                    Long lm = cacheItem.tagLastModifiedTs.get(tag);
                    if (lm != null) {
                        contentWriter.lastModified = lm;
                    }
                }
                contentWriter.isUseNormallyTag = true;
            }
            
            return contentWriter;
        }
        
        private static void init(ConfigContentWriter configInfo, String dataId, String group, String tenant,
                String tag, String vipServerTag, String requestIp, String requestIpApp,
                String isNotify) {
            configInfo.dataId = dataId;
            configInfo.group = group;
            configInfo.tenant = tenant;
            configInfo.tag = tag;
            configInfo.md5 = Constants.NULL;
            configInfo.vipServerTag = vipServerTag;
            configInfo.requestIp = requestIp;
            configInfo.requestIpApp = requestIpApp;
            configInfo.isNotify = Boolean.parseBoolean(isNotify);
        }
        
    }
}
