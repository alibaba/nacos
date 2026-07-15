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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.AiResourceImportHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Noop implementation of AI resource import handler.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
@ConditionalOnMissingBean(value = AiResourceImportHandler.class,
    ignored = AiResourceImportNoopHandler.class)
public class AiResourceImportNoopHandler implements AiResourceImportHandler {
    
    private static final String AI_IMPORT_NOT_ENABLED_MESSAGE =
        "Nacos AI resource import module is not enabled.";
    
    @Override
    public List<AiResourceImportSourceInfo> listSources(String resourceType)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AiResourceImportSearchResponse search(AiResourceImportSearchRequest request)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AiResourceImportValidateResponse validate(AiResourceImportValidateRequest request)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AiResourceImportExecuteResponse execute(AiResourceImportExecuteRequest request)
        throws NacosException {
        throw disabled();
    }
    
    private NacosException disabled() {
        return new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED, AI_IMPORT_NOT_ENABLED_MESSAGE);
    }
}
