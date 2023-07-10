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
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;
import com.alibaba.nacos.core.paramcheck.RpcParamExtractorManager;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigBatchListenRequestParamExtractorTest {
    
    private static ConfigBatchListenRequest req;
    
    @BeforeClass
    public static void initConfigBatchListenRequest() {
        req = new ConfigBatchListenRequest();
    }
    
    /**
     * Test extract param and check.
     */
    @Test
    public void testExtractParamAndCheck() throws Exception {
        RpcParamExtractorManager paramExtractorManager = RpcParamExtractorManager.getInstance();
        AbstractRpcParamExtractor extractor = paramExtractorManager.getExtractor(req.getClass().getSimpleName());
        assertEquals(extractor.getClass().getSimpleName(), ConfigBatchListenRequestParamExtractor.class.getSimpleName());
        extractor.extractParamAndCheck(req);
    }
}