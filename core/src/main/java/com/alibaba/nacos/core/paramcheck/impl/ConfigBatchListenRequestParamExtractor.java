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

import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Param extractor and checker for grpc config batch listen request{@link ConfigBatchListenRequest}.
 *
 * @author zhuoguang
 */
public class ConfigBatchListenRequestParamExtractor extends AbstractRpcParamExtractor {
    
    @Override
    public List<ParamInfo> extractParam(Request request) {
        ConfigBatchListenRequest req = (ConfigBatchListenRequest) request;
        List<ConfigBatchListenRequest.ConfigListenContext> configListenContextList = req.getConfigListenContexts();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        if (configListenContextList == null) {
            return paramInfos;
        }
        for (ConfigBatchListenRequest.ConfigListenContext configListenContext : configListenContextList) {
            ParamInfo configListContextParamInfo = new ParamInfo();
            configListContextParamInfo.setNamespaceId(configListenContext.getTenant());
            configListContextParamInfo.setGroup(configListenContext.getGroup());
            configListContextParamInfo.setDataId(configListenContext.getDataId());
            paramInfos.add(configListContextParamInfo);
        }
        return paramInfos;
    }
}
