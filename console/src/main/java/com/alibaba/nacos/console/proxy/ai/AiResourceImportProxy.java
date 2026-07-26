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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Proxy for Console AI resource import operations.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class AiResourceImportProxy {
    
    private final AiResourceImportHandler importHandler;
    
    public AiResourceImportProxy(AiResourceImportHandler importHandler) {
        this.importHandler = importHandler;
    }
    
    /**
     * List import sources.
     *
     * @param resourceType optional resource type filter
     * @return source list
     * @throws NacosException if source configuration is invalid
     */
    public List<AiResourceImportSourceInfo> listSources(String resourceType)
        throws NacosException {
        return importHandler.listSources(resourceType);
    }
    
    /**
     * Search external candidates.
     *
     * @param request search request
     * @return search response
     * @throws NacosException if the source cannot be searched
     */
    public AiResourceImportSearchResponse search(AiResourceImportSearchRequest request)
        throws NacosException {
        return importHandler.search(request);
    }
    
    /**
     * Validate selected candidates.
     *
     * @param request validate request
     * @return validation response
     * @throws NacosException if validation cannot start
     */
    public AiResourceImportValidateResponse validate(AiResourceImportValidateRequest request)
        throws NacosException {
        return importHandler.validate(request);
    }
    
    /**
     * Execute import for selected candidates.
     *
     * @param request execute request
     * @return execute response
     * @throws NacosException if import cannot start
     */
    public AiResourceImportExecuteResponse execute(AiResourceImportExecuteRequest request)
        throws NacosException {
        return importHandler.execute(request);
    }
}
