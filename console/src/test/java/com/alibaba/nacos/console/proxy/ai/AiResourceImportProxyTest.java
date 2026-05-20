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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.ai.AiResourceImportHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiResourceImportProxyTest {
    
    @Mock
    private AiResourceImportHandler importHandler;
    
    private AiResourceImportProxy importProxy;
    
    @BeforeEach
    void setUp() {
        importProxy = new AiResourceImportProxy(importHandler);
    }
    
    @Test
    void testListSources() throws NacosException {
        List<AiResourceImportSourceInfo> sources =
            Collections.singletonList(new AiResourceImportSourceInfo());
        when(importHandler.listSources("mcp")).thenReturn(sources);
        
        assertSame(sources, importProxy.listSources("mcp"));
        
        verify(importHandler).listSources("mcp");
    }
    
    @Test
    void testSearch() throws NacosException {
        AiResourceImportSearchRequest request = new AiResourceImportSearchRequest();
        AiResourceImportSearchResponse response = new AiResourceImportSearchResponse();
        when(importHandler.search(request)).thenReturn(response);
        
        assertSame(response, importProxy.search(request));
        
        verify(importHandler).search(request);
    }
    
    @Test
    void testValidate() throws NacosException {
        AiResourceImportValidateRequest request = new AiResourceImportValidateRequest();
        AiResourceImportValidateResponse response = new AiResourceImportValidateResponse();
        when(importHandler.validate(request)).thenReturn(response);
        
        assertSame(response, importProxy.validate(request));
        
        verify(importHandler).validate(request);
    }
    
    @Test
    void testExecute() throws NacosException {
        AiResourceImportExecuteRequest request = new AiResourceImportExecuteRequest();
        AiResourceImportExecuteResponse response = new AiResourceImportExecuteResponse();
        when(importHandler.execute(request)).thenReturn(response);
        
        assertSame(response, importProxy.execute(request));
        
        verify(importHandler).execute(request);
    }
}
