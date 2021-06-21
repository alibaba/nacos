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

import com.alibaba.nacos.api.config.remote.request.ConfigRemoveDraftRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.config.server.auth.ConfigResourceParser;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.control.TpsControl;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * ConfigRemoveDraftRequestHandler.
 *
 * @author wss
 * @version $Id: ConfigPublishDraftRequestHandler.java, v 0.1 2021年06月17日 4:59 PM wss Exp $
 */
@Component
public class ConfigRemoveDraftRequestHandler extends RequestHandler<ConfigRemoveDraftRequest, ConfigRemoveResponse> {

    private final PersistService persistService;

    public ConfigRemoveDraftRequestHandler(PersistService persistService) {
        this.persistService = persistService;
    }
    
    @Override
    @TpsControl(pointName = "ConfigDraftRemove")
    @Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
    public ConfigRemoveResponse handle(ConfigRemoveDraftRequest configRemoveDraftRequest, RequestMeta meta)
            throws NacosException {
        // check tenant
        String tenant = configRemoveDraftRequest.getTenant();
        String dataId = configRemoveDraftRequest.getDataId();
        String group = configRemoveDraftRequest.getGroup();
        String tag = configRemoveDraftRequest.getTag();
        
        try {
            ParamUtils.checkTenant(tenant);
            ParamUtils.checkParam(dataId, group, "datumId", "rm");
            ParamUtils.checkParam(tag);
            
            String clientIp = meta.getClientIp();
            if (StringUtils.isBlank(tag)) {
                persistService.removeDraftConfigInfo(dataId, group, tenant, clientIp, null);
            } else {
                return ConfigRemoveResponse
                        .buildFailResponse("remove draft config with tag is not supported yet.");
            }
            return ConfigRemoveResponse.buildSuccessResponse();
            
        } catch (Exception e) {
            Loggers.REMOTE_DIGEST.error("remove config error,error msg is {}", e.getMessage(), e);
            return ConfigRemoveResponse.buildFailResponse(e.getMessage());
        }
    }
    
}
