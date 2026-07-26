/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.ai.remote.request.QueryPromptRequest;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PromptRequestParamExtractorTest {
    
    private PromptRequestParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new PromptRequestParamExtractor();
    }
    
    @Test
    void extractParamWithPromptKey() throws Exception {
        QueryPromptRequest request = new QueryPromptRequest();
        request.setNamespaceId("ns-1");
        request.setPromptKey("myPrompt");
        
        List<ParamInfo> list = extractor.extractParam(request);
        
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("ns-1", list.get(0).getNamespaceId());
        assertEquals("myPrompt.json", list.get(0).getDataId());
    }
    
    @Test
    void extractParamWithBlankPromptKey() throws Exception {
        QueryPromptRequest request = new QueryPromptRequest();
        request.setNamespaceId("ns-2");
        request.setPromptKey("");
        
        List<ParamInfo> list = extractor.extractParam(request);
        
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("ns-2", list.get(0).getNamespaceId());
        assertNull(list.get(0).getDataId());
    }
}
