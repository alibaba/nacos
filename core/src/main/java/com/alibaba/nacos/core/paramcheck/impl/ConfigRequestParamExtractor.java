/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.paramcheck.impl;

import com.alibaba.nacos.api.config.remote.request.AbstractConfigRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.request.cluster.ConfigChangeClusterSyncRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The type Config request param extractor {@link AbstractConfigRequest}.
 *
 * @author zhuoguang
 */
public class ConfigRequestParamExtractor extends AbstractRpcParamExtractor {
    
    @Override
    public void init() {
        addTargetRequest(ConfigRemoveRequest.class.getSimpleName());
        addTargetRequest(ConfigQueryRequest.class.getSimpleName());
        addTargetRequest(ConfigPublishRequest.class.getSimpleName());
        addTargetRequest(ConfigChangeClusterSyncRequest.class.getSimpleName());
    }
    
    @Override
    public List<ParamInfo> extractParam(Request request) throws Exception {
        AbstractConfigRequest req = (AbstractConfigRequest) request;
        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setDataId(req.getDataId());
        paramInfo.setGroup(req.getGroup());
        paramInfo.setNamespaceId(req.getTenant());
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        return paramInfos;
    }
}
