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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HttpParamExtractor Manager.
 *
 * @author zhuoguang
 */
public class RpcParamExtractorManager {
    
    private static final RpcParamExtractorManager INSTANCE = new RpcParamExtractorManager();
    
    private static final AbstractRpcParamExtractor DEFAULT_EXTRACTOR = new AbstractRpcParamExtractor() {
        @Override
        public void init() {
        }
        
        @Override
        public void extractParamAndCheck(Request params) throws Exception {
        }
    };
    
    private final Map<String, AbstractRpcParamExtractor> extractorMap = new ConcurrentHashMap<>(32);
    
    private RpcParamExtractorManager() {
        Collection<AbstractRpcParamExtractor> extractors = NacosServiceLoader.load(AbstractRpcParamExtractor.class);
        for (AbstractRpcParamExtractor extractor : extractors) {
            List<String> targetrequestlist = extractor.getTargetRequestList();
            for (String targetRequest : targetrequestlist) {
                extractorMap.put(targetRequest, extractor);
            }
        }
    }
    
    public static RpcParamExtractorManager getInstance() {
        return INSTANCE;
    }
    
    public AbstractRpcParamExtractor getExtractor(String type) {
        if (StringUtils.isBlank(type)) {
            return DEFAULT_EXTRACTOR;
        }
        AbstractRpcParamExtractor extractor = extractorMap.get(type);
        if (extractor == null) {
            extractor = DEFAULT_EXTRACTOR;
        }
        return extractor;
    }
    
}
