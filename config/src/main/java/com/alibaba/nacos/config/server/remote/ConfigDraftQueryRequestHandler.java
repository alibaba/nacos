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

import com.alibaba.nacos.api.config.remote.request.ConfigDraftQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.config.server.auth.ConfigResourceParser;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.control.TpsControl;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import static com.alibaba.nacos.config.server.utils.RequestUtil.CLIENT_APPNAME_HEADER;

/**
 * ConfigDraftQueryRequestHandler.
 *
 * @author wss
 * @version $Id: ConfigDraftRequestHandler.java, v 0.1 2021年06月17日 4:59 PM wss Exp $
 */
@Component
public class ConfigDraftQueryRequestHandler extends RequestHandler<ConfigDraftQueryRequest, ConfigQueryResponse> {

    private static final int TRY_GET_LOCK_TIMES = 9;

    private final PersistService persistService;

    public ConfigDraftQueryRequestHandler(PersistService persistService) {
        this.persistService = persistService;
    }

    @Override
    @TpsControl(pointName = "ConfigQuery", parsers = {ConfigQueryGroupKeyParser.class, ConfigQueryGroupParser.class})
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public ConfigQueryResponse handle(ConfigDraftQueryRequest request, RequestMeta meta) throws NacosException {

        try {
            return getContext(request, meta, request.isNotify());
        } catch (Exception e) {
            return ConfigQueryResponse
                    .buildFailResponse(ResponseCode.FAIL.getCode(), e.getMessage());
        }

    }

    private ConfigQueryResponse getContext(ConfigDraftQueryRequest configDraftQueryRequest, RequestMeta meta, boolean notify) {

        ConfigQueryResponse response = new ConfigQueryResponse();
        ConfigInfoBase configInfoBase = null;
        try {
            String dataId = configDraftQueryRequest.getDataId();
            String group = configDraftQueryRequest.getGroup();
            String tenant = configDraftQueryRequest.getTenant();
            String clientIp = meta.getClientIp();
            String tag = configDraftQueryRequest.getTag();
            String requestIpApp = meta.getLabels().get(CLIENT_APPNAME_HEADER);

            if (StringUtils.isBlank(tag)) {
                configInfoBase = persistService.findDraftConfigInfo(dataId, group, tenant);
            } else {
                response.setErrorInfo(ConfigQueryResponse.CONFIG_QUERY_CONFLICT, "get draft config with tag is not supported yet.");
                return response;
            }
            if (configInfoBase == null) {
                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                        ConfigTraceService.PULL_EVENT_NOTFOUND, -1, clientIp, false);

                // pullLog.info("[client-get] clientIp={}, {},
                // no data",
                // new Object[]{clientIp, groupKey});

                response.setErrorInfo(ConfigQueryResponse.CONFIG_NOT_FOUND, "config data not exist");
                return response;
            }
        } catch (Exception e) {
            Loggers.REMOTE_DIGEST.error("[ConfigDraftQueryRequestHandler] draft config error ,request ={}",
                    configDraftQueryRequest, e);
            response.setErrorInfo(ResponseCode.FAIL.getCode(), e.getMessage());
            return response;
        }

        response.setMd5(configInfoBase.getMd5());
        response.setContent(configInfoBase.getContent());
        response.setResultCode(ResponseCode.SUCCESS.getCode());
        return response;
    }

    private static boolean isUseTag(CacheItem cacheItem, String tag) {
        if (cacheItem != null && cacheItem.tagMd5 != null && cacheItem.tagMd5.size() > 0) {
            return StringUtils.isNotBlank(tag) && cacheItem.tagMd5.containsKey(tag);
        }
        return false;
    }
}