/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.config.remote.request.ConfigPublishDraftRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.utils.MapUtil;
import com.alibaba.nacos.config.server.auth.ConfigResourceParser;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.control.TpsControl;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * ConfigPublishDraftRequestHandler.
 *
 * @author wss
 * @version $Id: ConfigPublishDraftRequestHandler.java, v 0.1 2021年06月17日 4:59 PM wss Exp $
 */
@Component
public class ConfigPublishDraftRequestHandler extends RequestHandler<ConfigPublishDraftRequest, ConfigPublishResponse> {

    private final PersistService persistService;

    public ConfigPublishDraftRequestHandler(PersistService persistService) {
        this.persistService = persistService;
    }

    @Override
    @TpsControl(pointName = "ConfigPublish", parsers = {ConfigPublishGroupKeyParser.class,
            ConfigPublishGroupParser.class})
    @Secured(action = ActionTypes.WRITE, resource = "", parser = ConfigResourceParser.class)
    public ConfigPublishResponse handle(ConfigPublishDraftRequest request, RequestMeta meta) throws NacosException {

        try {
            String dataId = request.getDataId();
            String group = request.getGroup();
            final String tenant = request.getTenant();

            final String srcIp = meta.getClientIp();
            final String requestIpApp = request.getAdditionParam("requestIpApp");
            final String tag = request.getAdditionParam("tag");
            final String appName = request.getAdditionParam("appName");
            final String type = request.getAdditionParam("type");
            final String srcUser = request.getAdditionParam("src_user");

            // check tenant
            ParamUtils.checkParam(dataId, group, "datumId", "pub");
            ParamUtils.checkParam(tag);
            Map<String, Object> configAdvanceInfo = new HashMap<String, Object>(10);
            MapUtil.putIfValNoNull(configAdvanceInfo, "config_tags", request.getAdditionParam("config_tags"));
            MapUtil.putIfValNoNull(configAdvanceInfo, "desc", request.getAdditionParam("desc"));
            MapUtil.putIfValNoNull(configAdvanceInfo, "use", request.getAdditionParam("use"));
            MapUtil.putIfValNoNull(configAdvanceInfo, "effect", request.getAdditionParam("effect"));
            MapUtil.putIfValNoNull(configAdvanceInfo, "type", type);
            MapUtil.putIfValNoNull(configAdvanceInfo, "schema", request.getAdditionParam("schema"));
            ParamUtils.checkParam(configAdvanceInfo);

            if (AggrWhitelist.isAggrDataId(dataId)) {
                Loggers.REMOTE_DIGEST
                        .warn("[aggr-conflict] {} attempt to publish single data from draft, {}, {}", srcIp, dataId, group);
                throw new NacosException(NacosException.NO_RIGHT, "dataId:" + dataId + " is aggr");
            }

            final Timestamp time = TimeUtils.getCurrentTime();
            String betaIps = request.getAdditionParam("betaIps");
            if (StringUtils.isBlank(betaIps)) {
                if (StringUtils.isBlank(tag)) {
                    if (StringUtils.isNotBlank(request.getCasMd5())) {
                        return ConfigPublishResponse
                                .buildFailResponse(ResponseCode.FAIL.getCode(), "Cas publish draft is not supported yet.");
                    } else {
                        ConfigInfo configInfo = persistService.publishDraftConfigInfo(dataId, group, tenant, srcIp, srcUser, time);
                        persistService.insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, false);
                        ConfigChangePublisher.notifyConfigChange(
                                new ConfigDataChangeEvent(false, dataId, group, tenant, time.getTime()));
                        ConfigTraceService
                                .logPersistenceEvent(dataId, group, tenant, requestIpApp, time.getTime(), InetUtils.getSelfIP(),
                                        ConfigTraceService.PERSISTENCE_EVENT_PUB, configInfo.getContent());
                    }
                } else {
                    return ConfigPublishResponse
                            .buildFailResponse(ResponseCode.FAIL.getCode(), "Publish draft with tag is not supported yet.");
                }
            } else {
                return ConfigPublishResponse
                        .buildFailResponse(ResponseCode.FAIL.getCode(), "Beta publish draft is not supported yet.");
            }
            return ConfigPublishResponse.buildSuccessResponse();
        } catch (Exception e) {
            Loggers.REMOTE_DIGEST.error("[ConfigPublishDraftRequestHandler] publish draft config error ,request ={}", request, e);
            return ConfigPublishResponse.buildFailResponse(
                    (e instanceof NacosException) ? ((NacosException) e).getErrCode() : ResponseCode.FAIL.getCode(),
                    e.getMessage());
        }
    }

}
