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

import com.alibaba.nacos.api.config.remote.request.ConfigBatchFuzzyWatchRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extractor for parameters of {@link ConfigBatchFuzzyWatchRequest}. This extractor retrieves parameter information
 * from the request object and constructs {@link ParamInfo} instances representing the namespace ID, group, and data IDs
 * contained in the request's contexts.
 *
 * @author stone-98
 * @date 2024/3/5
 */
public class ConfigBatchFuzzyListenRequestParamsExtractor extends AbstractRpcParamExtractor {
    
    /**
     * Extracts parameter information from the given request.
     *
     * @param request The request object to extract parameter information from.
     * @return A list of {@link ParamInfo} instances representing the extracted parameters.
     * @throws NacosException If an error occurs while extracting parameter information.
     */
    @Override
    public List<ParamInfo> extractParam(Request request) throws NacosException {
        ConfigBatchFuzzyWatchRequest req = (ConfigBatchFuzzyWatchRequest) request;
        Set<ConfigBatchFuzzyWatchRequest.Context> contexts = req.getContexts();
        List<ParamInfo> paramInfos = new ArrayList<>();
        if (contexts == null) {
            return paramInfos;
        }
        for (ConfigBatchFuzzyWatchRequest.Context context : contexts) {
            // Extract namespace ID and group from the context
            ParamInfo paramInfo1 = new ParamInfo();
            paramInfo1.setNamespaceId(context.getTenant());
            paramInfo1.setGroup(context.getGroup());
            paramInfos.add(paramInfo1);
            
            // Extract data IDs from the context if present
            if (CollectionUtils.isNotEmpty(context.getDataIds())) {
                for (String dataId : context.getDataIds()) {
                    ParamInfo paramInfo2 = new ParamInfo();
                    paramInfo2.setDataId(dataId);
                    paramInfos.add(paramInfo2);
                }
            }
        }
        return paramInfos;
    }
}
