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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.api.ai.model.prompt.PromptBasicInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptDetail;
import com.alibaba.nacos.api.ai.model.prompt.PromptHistoryItem;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Prompt operation service interface.
 *
 * @author nacos
 */
public interface PromptOperationService {
    
    /**
     * Publish a new version of prompt.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param version     version (a.b.c format)
     * @param template    template content
     * @param commitMsg   commit message
     * @param description description
     * @param srcUser     source user
     * @param srcIp       source IP
     * @return true if publish success
     * @throws NacosException if version validation fails or publish error
     */
    boolean publishPrompt(String namespaceId, String promptKey, String version, String template,
            String commitMsg, String description, String srcUser, String srcIp) throws NacosException;
    
    /**
     * Get prompt detail.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @return prompt detail, null if not found
     * @throws NacosException if query error
     */
    PromptDetail getPromptDetail(String namespaceId, String promptKey) throws NacosException;
    
    /**
     * Delete prompt.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param srcUser     source user
     * @param srcIp       source IP
     * @return true if delete success
     * @throws NacosException if delete error
     */
    boolean deletePrompt(String namespaceId, String promptKey, String srcUser, String srcIp) throws NacosException;
    
    /**
     * List prompts with pagination.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key filter (optional)
     * @param search      search mode: "accurate" or "blur"
     * @param pageNo      page number (1-based)
     * @param pageSize    page size
     * @return prompt list page
     * @throws NacosException if query error
     */
    Page<PromptBasicInfo> listPrompts(String namespaceId, String promptKey, String search, int pageNo, int pageSize)
            throws NacosException;
    
    /**
     * List prompt history versions.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param pageNo      page number (1-based)
     * @param pageSize    page size
     * @return history list page
     * @throws NacosException if query error
     */
    Page<PromptHistoryItem> listPromptHistory(String namespaceId, String promptKey, int pageNo, int pageSize)
            throws NacosException;
    
    /**
     * Get prompt history detail by history ID.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param historyId   history record ID
     * @return prompt detail of the history version
     * @throws NacosException if query error or not found
     */
    PromptDetail getPromptHistoryDetail(String namespaceId, String promptKey, Long historyId) throws NacosException;
    
    /**
     * Update prompt metadata (description only, without changing version).
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param description new description
     * @param srcUser     source user
     * @param srcIp       source IP
     * @return true if update success
     * @throws NacosException if update error
     */
    boolean updatePromptMetadata(String namespaceId, String promptKey, String description, String srcUser, String srcIp)
            throws NacosException;
}
