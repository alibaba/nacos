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

package com.alibaba.nacos.ai.param;

import com.alibaba.nacos.common.paramcheck.ParamInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiResourceSearchHttpParamExtractor}.
 */
class AiResourceSearchHttpParamExtractorTest {
    
    @Test
    void extractParamShouldProjectOnlyNamespace() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("namespaceId")).thenReturn("tenant-a");
        
        List<ParamInfo> result = new AiResourceSearchHttpParamExtractor().extractParam(request);
        
        assertEquals(1, result.size());
        assertEquals("tenant-a", result.get(0).getNamespaceId());
    }
}
