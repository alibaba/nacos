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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.importer.manager.AiResourceImportManager;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.ai.AiResourceImportHandler;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Inner implementation of Console AI resource import handler.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
@EnabledInnerHandler
@EnabledAiHandler
public class AiResourceImportInnerHandler implements AiResourceImportHandler {
    
    private final AiResourceImportManager importManager;
    
    public AiResourceImportInnerHandler(AiResourceImportManager importManager) {
        this.importManager = importManager;
    }
    
    @Override
    public List<AiResourceImportSourceInfo> listSources(String resourceType)
        throws NacosException {
        return importManager.listSources(resourceType);
    }
    
    @Override
    public AiResourceImportSearchResponse search(AiResourceImportSearchRequest request)
        throws NacosException {
        return importManager.search(request);
    }
    
    @Override
    public AiResourceImportValidateResponse validate(AiResourceImportValidateRequest request)
        throws NacosException {
        return importManager.validate(request);
    }
    
    @Override
    public AiResourceImportExecuteResponse execute(AiResourceImportExecuteRequest request)
        throws NacosException {
        return importManager.execute(request);
    }
}
