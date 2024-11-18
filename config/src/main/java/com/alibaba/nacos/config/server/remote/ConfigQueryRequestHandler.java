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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.ConfigRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.CLIENT_IP;
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
    
    public ConfigQueryRequestHandler() {
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
        
        String groupKey = GroupKey2.getKey(configQueryRequest.getDataId(), configQueryRequest.getGroup(),
                configQueryRequest.getTenant());
        String requestIpApp = meta.getLabels().get(CLIENT_APPNAME_HEADER);
        String acceptCharset = ENCODE_UTF8;
        ParamUtils.checkParam(tag);
        int lockResult = ConfigCacheService.tryConfigReadLock(groupKey);
        String pullEvent = ConfigTraceService.PULL_EVENT;
        String pullType = ConfigTraceService.PULL_TYPE_OK;
        
        ConfigQueryResponse response = new ConfigQueryResponse();
        CacheItem cacheItem = ConfigCacheService.getContentCache(groupKey);
        
        if (lockResult > 0 && cacheItem != null) {
            try {
                long lastModified = 0L;
                String configType = cacheItem.getType();
                response.setContentType((null != configType) ? configType : "text");
                
                String content;
                String md5;
                String encryptedDataKey;
                ConfigCacheGray matchedGray = null;
                Map<String, String> appLabels = null;
                boolean specificTag = StringUtils.isNotBlank(tag);
                if (specificTag) {
                    appLabels = new HashMap<>(4);
                    appLabels.put(TagGrayRule.VIP_SERVER_TAG_LABEL, tag);
                    appLabels.put(CLIENT_IP, clientIp);
                } else {
                    appLabels = new HashMap(meta.getAppLabels());
                    if (!appLabels.containsKey(CLIENT_IP)) {
                        appLabels.put(CLIENT_IP, clientIp);
                    }
                }
                
                if (cacheItem.getSortConfigGrays() != null && !cacheItem.getSortConfigGrays().isEmpty()) {
                    for (ConfigCacheGray configCacheGray : cacheItem.getSortConfigGrays()) {
                        if (configCacheGray.match(appLabels)) {
                            matchedGray = configCacheGray;
                            break;
                        }
                    }
                }
                if (matchedGray != null) {
                    md5 = matchedGray.getMd5(acceptCharset);
                    lastModified = matchedGray.getLastModifiedTs();
                    encryptedDataKey = matchedGray.getEncryptedDataKey();
                    content = ConfigDiskServiceFactory.getInstance()
                            .getGrayContent(dataId, group, tenant, matchedGray.getGrayName());
                    pullEvent = ConfigTraceService.PULL_EVENT + "-" + matchedGray.getGrayName();
                    if (BetaGrayRule.TYPE_BETA.equals(matchedGray.getGrayName())) {
                        response.setBeta(true);
                    }
                    if (TagGrayRule.TYPE_TAG.equals(matchedGray.getGrayRule().getType())) {
                        response.setTag(URLEncoder.encode(matchedGray.getRawGrayRule(), ENCODE_UTF8));
                    }
                } else if (specificTag) {
                    //specific tag is not found
                    md5 = null;
                    lastModified = 0L;
                    encryptedDataKey = null;
                    content = null;
                    pullEvent = ConfigTraceService.PULL_EVENT + "-" + TagGrayRule.TYPE_TAG + "-" + tag;
                    response.setTag(tag);
                } else {
                    md5 = cacheItem.getConfigCache().getMd5(acceptCharset);
                    lastModified = cacheItem.getConfigCache().getLastModifiedTs();
                    encryptedDataKey = cacheItem.getConfigCache().getEncryptedDataKey();
                    content = ConfigDiskServiceFactory.getInstance().getContent(dataId, group, tenant);
                    pullEvent = ConfigTraceService.PULL_EVENT;
                }
                
                response.setMd5(md5);
                response.setEncryptedDataKey(encryptedDataKey);
                response.setContent(content);
                response.setLastModified(lastModified);
                if (content == null) {
                    pullType = ConfigTraceService.PULL_TYPE_NOTFOUND;
                    response.setErrorInfo(ConfigQueryResponse.CONFIG_NOT_FOUND, "config data not exist");
                } else {
                    response.setResultCode(ResponseCode.SUCCESS.getCode());
                }
                LogUtil.PULL_CHECK_LOG.warn("{}|{}|{}|{}", groupKey, clientIp, md5, TimeUtils.getCurrentTimeStr());
                
                final long delayed = notify ? -1 : System.currentTimeMillis() - lastModified;
                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, lastModified, pullEvent, pullType,
                        delayed, clientIp, notify, "grpc");
            } finally {
                ConfigCacheService.releaseReadLock(groupKey);
            }
        } else if (lockResult == 0 || cacheItem == null) {
            
            //CacheItem No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
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
    
}
