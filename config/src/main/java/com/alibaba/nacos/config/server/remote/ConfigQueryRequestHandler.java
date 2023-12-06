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
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.ConfigRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
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
    
    private static final int TRY_GET_LOCK_TIMES = 9;
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final ConfigInfoTagPersistService configInfoTagPersistService;
    
    private final ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    public ConfigQueryRequestHandler(ConfigInfoPersistService configInfoPersistService,
            ConfigInfoTagPersistService configInfoTagPersistService,
            ConfigInfoBetaPersistService configInfoBetaPersistService) {
        this.configInfoPersistService = configInfoPersistService;
        this.configInfoTagPersistService = configInfoTagPersistService;
        this.configInfoBetaPersistService = configInfoBetaPersistService;
    }
    
    @Override
    @TpsControl(pointName = "ConfigQuery")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @ExtractorManager.Extractor(rpcExtractor = ConfigRequestParamExtractor.class)
    public ConfigQueryResponse handle(ConfigQueryRequest request, RequestMeta meta) throws NacosException {
        
        try {
            return getContext(request, meta, request.isNotify());
        } catch (Exception e) {
            return ConfigQueryResponse.buildFailResponse(ResponseCode.FAIL.getCode(), e.getMessage());
        }
        
    }
    
    private ConfigQueryResponse getContext(ConfigQueryRequest configQueryRequest, RequestMeta meta, boolean notify)
            throws Exception {
        String dataId = configQueryRequest.getDataId();
        String group = configQueryRequest.getGroup();
        String tenant = configQueryRequest.getTenant();
        String clientIp = meta.getClientIp();
        String tag = configQueryRequest.getTag();
        ConfigQueryResponse response = new ConfigQueryResponse();
        
        final String groupKey = GroupKey2.getKey(configQueryRequest.getDataId(), configQueryRequest.getGroup(),
                configQueryRequest.getTenant());
        
        String autoTag = configQueryRequest.getHeader(com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG);
        
        String requestIpApp = meta.getLabels().get(CLIENT_APPNAME_HEADER);
        
        String acceptCharset = ENCODE_UTF8;
        
        int lockResult = tryConfigReadLock(groupKey);
        String pullEvent = ConfigTraceService.PULL_EVENT;
        boolean isBeta = false;
        if (lockResult > 0) {
            try {
                String md5 = Constants.NULL;
                long lastModified = 0L;
                CacheItem cacheItem = ConfigCacheService.getContentCache(groupKey);
                if (cacheItem != null) {
                    if (cacheItem.isBeta() && cacheItem.getIps4Beta() != null && cacheItem.getIps4Beta()
                            .contains(clientIp) && cacheItem.getConfigCacheBeta() != null) {
                        isBeta = true;
                    }
                    String configType = cacheItem.getType();
                    response.setContentType((null != configType) ? configType : "text");
                }
                String content = null;
                ConfigInfoBase configInfoBase = null;
                if (isBeta) {
                    md5 = cacheItem.getConfigCacheBeta().getMd5(acceptCharset);
                    lastModified = cacheItem.getConfigCacheBeta().getLastModifiedTs();
                    if (PropertyUtil.isDirectRead()) {
                        configInfoBase = configInfoBetaPersistService.findConfigInfo4Beta(dataId, group, tenant);
                    } else {
                        content = ConfigDiskServiceFactory.getInstance().getBetaContent(dataId, group, tenant);
                    }
                    pullEvent = ConfigTraceService.PULL_EVENT_BETA;
                    response.setBeta(true);
                } else {
                    if (StringUtils.isBlank(tag)) {
                        if (isUseTag(cacheItem, autoTag)) {
                            if (cacheItem != null) {
                                md5 = cacheItem.getTagMd5(autoTag, acceptCharset);
                                lastModified = cacheItem.getTagLastModified(autoTag);
                            }
                            if (PropertyUtil.isDirectRead()) {
                                configInfoBase = configInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant,
                                        autoTag);
                            } else {
                                content = ConfigDiskServiceFactory.getInstance()
                                        .getTagContent(dataId, group, tenant, autoTag);
                            }
                            pullEvent = ConfigTraceService.PULL_EVENT_TAG + "-" + autoTag;
                            response.setTag(URLEncoder.encode(autoTag, ENCODE_UTF8));
                            
                        } else {
                            md5 = cacheItem.getConfigCache().getMd5(acceptCharset);
                            lastModified = cacheItem.getConfigCache().getLastModifiedTs();
                            
                            if (PropertyUtil.isDirectRead()) {
                                configInfoBase = configInfoPersistService.findConfigInfo(dataId, group, tenant);
                            } else {
                                content = ConfigDiskServiceFactory.getInstance().getContent(dataId, group, tenant);
                            }
                            pullEvent = ConfigTraceService.PULL_EVENT;
                            if (configInfoBase == null && content == null) {
                                // No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
                                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, pullEvent,
                                        ConfigTraceService.PULL_TYPE_NOTFOUND, -1, clientIp, false, "grpc");
                                
                                response.setErrorInfo(ConfigQueryResponse.CONFIG_NOT_FOUND, "config data not exist");
                                return response;
                            }
                        }
                    } else {
                        if (cacheItem != null) {
                            md5 = cacheItem.getTagMd5(tag, acceptCharset);
                            lastModified = cacheItem.getTagLastModified(tag);
                        }
                        if (PropertyUtil.isDirectRead()) {
                            configInfoBase = configInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant, tag);
                        } else {
                            content = ConfigDiskServiceFactory.getInstance().getTagContent(dataId, group, tenant, tag);
                        }
                        response.setTag(tag);
                        pullEvent = ConfigTraceService.PULL_EVENT_TAG + "-" + tag;
                        if (configInfoBase == null && content == null) {
                            // No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
                            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, pullEvent,
                                    ConfigTraceService.PULL_TYPE_NOTFOUND, -1, clientIp, false, "grpc");
                            
                            response.setErrorInfo(ConfigQueryResponse.CONFIG_NOT_FOUND, "config data not exist");
                            return response;
                        }
                    }
                }
                
                response.setMd5(md5);
                String encryptedDataKey = getEncryptedDataKey(tag, clientIp, cacheItem, isBeta, autoTag);
                response.setEncryptedDataKey(encryptedDataKey);
                if (PropertyUtil.isDirectRead()) {
                    response.setLastModified(lastModified);
                    response.setContent(configInfoBase.getContent());
                    response.setResultCode(ResponseCode.SUCCESS.getCode());
                } else {
                    response.setContent(content);
                    response.setLastModified(lastModified);
                    response.setResultCode(ResponseCode.SUCCESS.getCode());
                }
                
                LogUtil.PULL_CHECK_LOG.warn("{}|{}|{}|{}", groupKey, clientIp, md5, TimeUtils.getCurrentTimeStr());
                
                final long delayed = notify ? -1 : System.currentTimeMillis() - lastModified;
                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, lastModified, pullEvent,
                        ConfigTraceService.PULL_TYPE_OK, delayed, clientIp, notify, "grpc");
            } finally {
                releaseConfigReadLock(groupKey);
            }
        } else if (lockResult == 0) {
            
            // FIXME CacheItem No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, pullEvent,
                    ConfigTraceService.PULL_TYPE_NOTFOUND, -1, clientIp, notify, "grpc");
            response.setErrorInfo(ConfigQueryResponse.CONFIG_NOT_FOUND, "config data not exist");
            
        } else {
            PULL_LOG.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);
            response.setErrorInfo(ConfigQueryResponse.CONFIG_QUERY_CONFLICT,
                    "requested file is being modified, please try later.");
        }
        return response;
    }
    
    private String getEncryptedDataKey(String tag, String clientIp, CacheItem cacheItem, boolean isBeta,
            String autoTag) {
        if (cacheItem == null) {
            return null;
        }
        String encryptedDataKey;
        if (isBeta && cacheItem.getConfigCacheBeta() != null) {
            encryptedDataKey = cacheItem.getConfigCacheBeta().getEncryptedDataKey();
        } else {
            if (StringUtils.isBlank(tag)) {
                if (isUseTag(cacheItem, autoTag)) {
                    encryptedDataKey = cacheItem.getTagEncryptedDataKey(autoTag);
                } else if (cacheItem.isBatch && cacheItem.delimiter >= InternetAddressUtil.ipToInt(clientIp)
                        && cacheItem.getConfigCacheBatch() != null) {
                    // batch
                    encryptedDataKey = cacheItem.getConfigCacheBatch().getEncryptedDataKey();
                } else {
                    encryptedDataKey = cacheItem.getConfigCache().getEncryptedDataKey();
                }
            } else {
                encryptedDataKey = cacheItem.getTagEncryptedDataKey(tag);
            }
        }
        return encryptedDataKey;
    }
    
    private static void releaseConfigReadLock(String groupKey) {
        ConfigCacheService.releaseReadLock(groupKey);
    }
    
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
        return StringUtils.isNotBlank(tag) && cacheItem.getConfigCacheTags() != null && cacheItem.getConfigCacheTags()
                .containsKey(tag);
    }
    
}
