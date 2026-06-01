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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Prompt client operation service implementation.
 *
 * <p>Delegates runtime prompt queries to {@link PromptOperationService} which reads from
 * DB metadata (ai_resource + ai_resource_version) and SPI storage (AiResourceStorageRouter).
 * Adds MD5-based conditional fetch for SDK client long-polling.</p>
 *
 * @author nacos
 */
@Service
public class PromptClientOperationServiceImpl implements PromptClientOperationService {
    
    private static final String ENCODE_UTF8 = "UTF-8";
    
    private final PromptOperationService promptOperationService;
    
    public PromptClientOperationServiceImpl(@Lazy PromptOperationService promptOperationService) {
        this.promptOperationService = promptOperationService;
    }
    
    @Override
    public PromptVersionInfo queryPrompt(String namespaceId, String promptKey, String version,
        String label, String md5)
        throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `promptKey` not present");
        }
        PromptVersionInfo result =
            promptOperationService.queryPrompt(namespaceId, promptKey, version, label);
        String currentMd5 = result.getMd5();
        if (StringUtils.isNotBlank(md5) && md5.equals(currentMd5)) {
            throw new NacosException(NacosException.NOT_MODIFIED, "prompt data is up to date");
        }
        return result;
    }
}
