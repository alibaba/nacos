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

import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Extractor for parameters of {@link ConfigFuzzyWatchRequest}. This extractor retrieves parameter information from the
 * request object and constructs {@link ParamInfo} instances representing the namespace ID, group, and data IDs
 * contained in the request's contexts.
 *
 * @author stone-98
 * @date 2024/3/5
 */
public class ConfigFuzzyWatchRequestParamsExtractor extends AbstractRpcParamExtractor {
    
    /**
     * Extracts parameter information from the given request.
     *
     * @param request The request object to extract parameter information from.
     * @return A list of {@link ParamInfo} instances representing the extracted parameters.
     * @throws NacosException If an error occurs while extracting parameter information.
     */
    @Override
    public List<ParamInfo> extractParam(Request request) throws NacosException {
        ConfigFuzzyWatchRequest req = (ConfigFuzzyWatchRequest) request;
        List<ParamInfo> paramInfos = new ArrayList<>();
        // Extract namespace ID and group from the context
        ParamInfo paramInfo1 = new ParamInfo();
        paramInfo1.setNamespaceId(FuzzyGroupKeyPattern.getNamespaceFromPattern(req.getGroupKeyPattern()));
        paramInfos.add(paramInfo1);
        return paramInfos;
    }
}
