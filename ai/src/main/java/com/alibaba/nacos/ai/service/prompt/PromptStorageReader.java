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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Exact Prompt content reader shared by ordinary reads and fenced Hub adapters. */
@Service
public class PromptStorageReader {
    
    private final AiResourceStorageRouter storageRouter;
    
    public PromptStorageReader() {
        this(AiResourceStorageRouter.getInstance());
    }
    
    PromptStorageReader(AiResourceStorageRouter storageRouter) {
        this.storageRouter = storageRouter;
    }
    
    /**
     * Read an exact persisted version without granting request authorization or resolving labels.
     * The caller must establish source READ or a validated fixed Hub publication around this IO.
     */
    public PromptVersionInfo read(String namespaceId, String promptKey, String version,
        String storageJson) throws NacosException {
        String provider = parseStorageProvider(storageJson);
        StorageKey storageKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
            AiResourceConstants.RESOURCE_TYPE_PROMPT, promptKey, version,
            PromptUtils.PROMPT_MAIN_DATA_ID);
        byte[] data = storageRouter.route(storageKey).get(storageKey);
        if (data == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Prompt content not found: " + promptKey + "@" + version);
        }
        PromptVersionInfo result =
            JacksonUtils.toObj(new String(data, StandardCharsets.UTF_8), PromptVersionInfo.class);
        result.setPromptKey(promptKey);
        result.setVersion(version);
        return result;
    }
    
    private static String parseStorageProvider(String storageJson) {
        if (StringUtils.isNotBlank(storageJson)) {
            try {
                Map<String, Object> storage = JacksonUtils.toObj(storageJson, Map.class);
                Object provider = storage.get("provider");
                if (provider instanceof String && StringUtils.isNotBlank((String) provider)) {
                    return ((String) provider).trim();
                }
            } catch (Exception ignored) {
            }
        }
        return NacosConfigAiResourceStorage.TYPE;
    }
}
