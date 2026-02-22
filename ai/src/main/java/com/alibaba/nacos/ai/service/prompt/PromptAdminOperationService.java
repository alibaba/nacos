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

import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.List;

/**
 * Prompt admin operation service.
 *
 * @author nacos
 */
public interface PromptAdminOperationService {
    
    /**
     * Publish prompt version boolean.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param version     the version
     * @param template    the template
     * @param commitMsg   the commit msg
     * @param description the description
     * @param bizTags     the biz tags
     * @param srcUser     the src user
     * @param srcIp       the src ip
     * @return the boolean
     * @throws NacosException the nacos exception
     */
    boolean publishPromptVersion(String namespaceId, String promptKey, String version, String template, String commitMsg,
            String description, List<String> bizTags, String srcUser, String srcIp) throws NacosException;
    
    /**
     * Bind label boolean.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param label       the label
     * @param version     the version
     * @param srcUser     the src user
     * @param srcIp       the src ip
     * @return the boolean
     * @throws NacosException the nacos exception
     */
    boolean bindLabel(String namespaceId, String promptKey, String label, String version, String srcUser, String srcIp)
            throws NacosException;
    
    /**
     * Unbind label boolean.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param label       the label
     * @param srcUser     the src user
     * @param srcIp       the src ip
     * @return the boolean
     * @throws NacosException the nacos exception
     */
    boolean unbindLabel(String namespaceId, String promptKey, String label, String srcUser, String srcIp)
            throws NacosException;
    
    /**
     * Delete prompt boolean.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param srcUser     the src user
     * @param srcIp       the src ip
     * @return the boolean
     * @throws NacosException the nacos exception
     */
    boolean deletePrompt(String namespaceId, String promptKey, String srcUser, String srcIp) throws NacosException;
    
    /**
     * Update prompt metadata boolean.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param description the description
     * @param bizTags     the biz tags
     * @param srcUser     the src user
     * @param srcIp       the src ip
     * @return the boolean
     * @throws NacosException the nacos exception
     */
    boolean updatePromptMetadata(String namespaceId, String promptKey, String description, List<String> bizTags, String srcUser,
            String srcIp) throws NacosException;
    
    /**
     * List prompts page.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param search      the search
     * @param bizTags     the biz tags
     * @param pageNo      the page no
     * @param pageSize    the page size
     * @return the page
     * @throws NacosException the nacos exception
     */
    Page<PromptMetaSummary> listPrompts(String namespaceId, String promptKey, String search, String bizTags, int pageNo,
            int pageSize) throws NacosException;
    
    /**
     * List prompt versions page.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param pageNo      the page no
     * @param pageSize    the page size
     * @return the page
     * @throws NacosException the nacos exception
     */
    Page<PromptVersionSummary> listPromptVersions(String namespaceId, String promptKey, int pageNo, int pageSize)
            throws NacosException;
    
    /**
     * Gets prompt meta.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @return the prompt meta
     * @throws NacosException the nacos exception
     */
    PromptMetaInfo getPromptMeta(String namespaceId, String promptKey) throws NacosException;
    
    /**
     * Query prompt detail prompt version info.
     *
     * @param namespaceId the namespace id
     * @param promptKey   the prompt key
     * @param version     the version
     * @param label       the label
     * @return the prompt version info
     * @throws NacosException the nacos exception
     */
    PromptVersionInfo queryPromptDetail(String namespaceId, String promptKey, String version, String label) throws NacosException;
}
