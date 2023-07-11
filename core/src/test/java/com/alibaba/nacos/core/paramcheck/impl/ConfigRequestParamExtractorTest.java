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
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;
import com.alibaba.nacos.core.paramcheck.RpcParamExtractorManager;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigRequestParamExtractorTest {
    
    private static AbstractConfigRequest req1;
    
    private static AbstractConfigRequest req2;
    
    private static AbstractConfigRequest req3;
    
    private static AbstractConfigRequest req4;
    
    @BeforeClass
    public static void initAbstractConfigRequest() {
        req1 = new ConfigPublishRequest();
        req2 = new ConfigQueryRequest();
        req3 = new ConfigRemoveRequest();
        req4 = new ConfigChangeClusterSyncRequest();
    }
    
    /**
     * Test extract param and check.
     */
    @Test
    public void testExtractParamAndCheck() throws Exception {
        RpcParamExtractorManager paramExtractorManager = RpcParamExtractorManager.getInstance();
        AbstractRpcParamExtractor extractor1 = paramExtractorManager.getExtractor(req1.getClass().getSimpleName());
        assertEquals(extractor1.getClass().getSimpleName(), ConfigRequestParamExtractor.class.getSimpleName());
        extractor1.extractParamAndCheck(req1);
        
        AbstractRpcParamExtractor extractor2 = paramExtractorManager.getExtractor(req2.getClass().getSimpleName());
        assertEquals(extractor2.getClass().getSimpleName(), ConfigRequestParamExtractor.class.getSimpleName());
        extractor2.extractParamAndCheck(req2);
        
        AbstractRpcParamExtractor extractor3 = paramExtractorManager.getExtractor(req3.getClass().getSimpleName());
        assertEquals(extractor3.getClass().getSimpleName(), ConfigRequestParamExtractor.class.getSimpleName());
        extractor3.extractParamAndCheck(req3);
        
        AbstractRpcParamExtractor extractor4 = paramExtractorManager.getExtractor(req4.getClass().getSimpleName());
        assertEquals(extractor4.getClass().getSimpleName(), ConfigRequestParamExtractor.class.getSimpleName());
        extractor4.extractParamAndCheck(req4);
    }
}