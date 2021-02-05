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

import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.config.server.auth.ConfigResourceParser;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.control.TpsControl;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * handler to remove config.
 *
 * @author liuzunfei
 * @version $Id: ConfiRemoveRequestHandler.java, v 0.1 2020年07月16日 5:49 PM liuzunfei Exp $
 */
@Component
public class ConfigRemoveRequestHandler extends RequestHandler<ConfigRemoveRequest, ConfigRemoveResponse> {
    
    private final PersistService persistService;
    
    public ConfigRemoveRequestHandler(PersistService persistService) {
        this.persistService = persistService;
    }
    
    @Override
    @TpsControl(pointName = "ConfigRemove")
    @Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
    public ConfigRemoveResponse handle(ConfigRemoveRequest configRemoveRequest, RequestMeta meta)
            throws NacosException {
        // check tenant
        String tenant = configRemoveRequest.getTenant();
        String dataId = configRemoveRequest.getDataId();
        String group = configRemoveRequest.getGroup();
        String tag = configRemoveRequest.getTag();
        
        try {
            ParamUtils.checkTenant(tenant);
            ParamUtils.checkParam(dataId, group, "datumId", "rm");
            ParamUtils.checkParam(tag);
            
            String clientIp = meta.getClientIp();
            if (StringUtils.isBlank(tag)) {
                persistService.removeConfigInfo(dataId, group, tenant, clientIp, null);
            } else {
                persistService.removeConfigInfoTag(dataId, group, tenant, tag, clientIp, null);
            }
            final Timestamp time = TimeUtils.getCurrentTime();
            ConfigTraceService.logPersistenceEvent(dataId, group, tenant, null, time.getTime(), clientIp,
                    ConfigTraceService.PERSISTENCE_EVENT_REMOVE, null);
            ConfigChangePublisher
                    .notifyConfigChange(new ConfigDataChangeEvent(false, dataId, group, tenant, tag, time.getTime()));
            return ConfigRemoveResponse.buildSuccessResponse();
            
        } catch (Exception e) {
            Loggers.REMOTE_DIGEST.error("remove config error,error msg is {}", e.getMessage(), e);
            return ConfigRemoveResponse.buildFailResponse(e.getMessage());
        }
    }
    
}
