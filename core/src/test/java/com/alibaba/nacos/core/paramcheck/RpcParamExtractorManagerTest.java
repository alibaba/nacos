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

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import junit.framework.TestCase;

/**
 * The type Rpc param extractor manager test.
 */
public class RpcParamExtractorManagerTest extends TestCase {
    
    /**
     * Test get instance.
     */
    public void testGetInstance() {
        RpcParamExtractorManager paramExtractorManager = RpcParamExtractorManager.getInstance();
    }
    
    /**
     * Test get extractor.
     *
     * @throws Exception the exception
     */
    public void testGetExtractor() throws Exception {
        RpcParamExtractorManager paramExtractorManager = RpcParamExtractorManager.getInstance();
        ConfigQueryRequest request = new ConfigQueryRequest();
        AbstractRpcParamExtractor extractor = paramExtractorManager.getExtractor(request.getClass().getSimpleName());
        extractor.extractParam(request);
    }
}