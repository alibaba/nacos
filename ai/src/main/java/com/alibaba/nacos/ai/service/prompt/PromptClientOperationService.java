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

/**
 * Prompt client operation service.
 *
 * <p>Handles runtime prompt queries for SDK clients, including MD5-based conditional fetch.</p>
 *
 * @author nacos
 */
public interface PromptClientOperationService {
    
    /**
     * Query prompt by version/label/latest with priority version > label > latest.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param version     the version
     * @param label       the label
     * @param md5         the client md5 for conditional query
     * @return the prompt version info
     * @throws NacosException the nacos exception
     */
    PromptVersionInfo queryPrompt(String namespaceId, String promptKey, String version,
        String label, String md5)
        throws NacosException;
    
    /**
     * Query prompt by version/label/latest with priority version > label > latest.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param version     the version
     * @param label       the label
     * @return the prompt version info
     * @throws NacosException the nacos exception
     */
    default PromptVersionInfo queryPrompt(String namespaceId, String promptKey, String version,
        String label)
        throws NacosException {
        return queryPrompt(namespaceId, promptKey, version, label, null);
    }
}
