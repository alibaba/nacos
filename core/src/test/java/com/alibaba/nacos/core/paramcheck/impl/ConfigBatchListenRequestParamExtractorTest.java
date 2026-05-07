/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigBatchListenRequestParamExtractor} unit test.
 */
class ConfigBatchListenRequestParamExtractorTest {
    
    private ConfigBatchListenRequestParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new ConfigBatchListenRequestParamExtractor();
    }
    
    @Test
    void extractParamWithNullContexts() {
        ConfigBatchListenRequest request = new ConfigBatchListenRequest();
        request.setConfigListenContexts(null);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }
    
    @Test
    void extractParamWithContexts() {
        ConfigBatchListenRequest request = new ConfigBatchListenRequest();
        request.addConfigListenContext("g1", "dataId1", "tenant1", "md5-1");
        request.addConfigListenContext("g2", "dataId2", "tenant2", "md5-2");
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("tenant1", list.get(0).getNamespaceId());
        assertEquals("g1", list.get(0).getGroup());
        assertEquals("dataId1", list.get(0).getDataId());
        assertEquals("tenant2", list.get(1).getNamespaceId());
        assertEquals("g2", list.get(1).getGroup());
        assertEquals("dataId2", list.get(1).getDataId());
    }
}
