/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * PromptService operations obtained from {@link AiService#prompt()}.
 *
 * @author Nacos
 * @since 3.3.0
 */
public interface PromptService {
    
    /**
     * Get prompt by prompt key.
     *
     * @param promptKey prompt key (unique identifier)
     * @return prompt object with current version
     * @throws NacosException if prompt not found or query error
     */
    @Since("3.2.0")
    Prompt getPrompt(String promptKey) throws NacosException;
    
    /**
     * Get prompt by prompt key and target version.
     *
     * @param promptKey prompt key (unique identifier)
     * @param version target prompt version, if null, will get latest version
     * @return prompt object with target version
     * @throws NacosException if prompt not found or query error
     */
    @Since("3.2.0")
    Prompt getPromptByVersion(String promptKey, String version) throws NacosException;
    
    /**
     * Get prompt by prompt key and target label.
     *
     * @param promptKey prompt key (unique identifier)
     * @param label target prompt label
     * @return prompt object with target label
     * @throws NacosException if prompt not found or query error
     */
    @Since("3.2.0")
    Prompt getPromptByLabel(String promptKey, String label) throws NacosException;
    
    /**
     * Subscribe prompt changes.
     *
     * @param promptKey      prompt key
     * @param version        target prompt version, optional
     * @param label          target prompt label, optional
     * @param promptListener listener for prompt changes
     * @return current prompt object, may be null if prompt not found
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.0")
    Prompt subscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException;
    
    /**
     * Un-subscribe prompt changes.
     *
     * @param promptKey      prompt key
     * @param version        target prompt version, optional
     * @param label          target prompt label, optional
     * @param promptListener listener for prompt changes
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.0")
    void unsubscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException;
}
