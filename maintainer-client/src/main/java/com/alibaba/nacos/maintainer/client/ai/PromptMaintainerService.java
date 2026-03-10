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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Nacos AI module Prompt relative maintainer service.
 *
 * @author nacos
 */
public interface PromptMaintainerService {
    
    /**
     * List prompts with pagination.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key pattern for filtering
     * @param search      search mode: "accurate" or "blur"
     * @param bizTags     biz tags filter (comma-separated)
     * @param pageNo      page number
     * @param pageSize    page size
     * @return paged prompt list
     * @throws NacosException if fail to list prompts
     */
    Page<PromptMetaSummary> listPrompts(String namespaceId, String promptKey, String search, String bizTags, int pageNo,
            int pageSize)
            throws NacosException;
    
    /**
     * List prompts with default namespace.
     *
     * @param promptKey prompt key pattern for filtering
     * @param pageNo    page number
     * @param pageSize  page size
     * @return paged prompt list
     * @throws NacosException if fail to list prompts
     */
    default Page<PromptMetaSummary> listPrompts(String promptKey, int pageNo, int pageSize) throws NacosException {
        return listPrompts(Constants.DEFAULT_NAMESPACE_ID, promptKey, "blur", null, pageNo, pageSize);
    }
    
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
     * Gets prompt meta.
     *
     * @param promptKey the prompt key
     * @return the prompt meta
     * @throws NacosException the nacos exception
     */
    default PromptMetaInfo getPromptMeta(String promptKey) throws NacosException {
        return getPromptMeta(Constants.DEFAULT_NAMESPACE_ID, promptKey);
    }
    
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
    PromptVersionInfo queryPromptDetail(String namespaceId, String promptKey, String version, String label)
            throws NacosException;
    
    /**
     * Bind prompt label to version.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param label       prompt label
     * @param version     prompt version
     * @return true if bind success
     * @throws NacosException if bind fails
     */
    boolean bindLabel(String namespaceId, String promptKey, String label, String version) throws NacosException;
    
    /**
     * Unbind prompt label.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param label       prompt label
     * @return true if unbind success
     * @throws NacosException if unbind fails
     */
    boolean unbindLabel(String namespaceId, String promptKey, String label) throws NacosException;
    
    /**
     * Publish a new version of prompt.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param version     version (must be greater than current version)
     * @param template    prompt template content
     * @param commitMsg   commit message
     * @param description prompt description
     * @param bizTags     biz tags (comma-separated)
     * @return true if publish success
     * @throws NacosException if fail to publish prompt
     */
    boolean publishPrompt(String namespaceId, String promptKey, String version, String template,
            String commitMsg, String description, String bizTags) throws NacosException;
    
    /**
     * Publish a new version of prompt without tags.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param version     version (must be greater than current version)
     * @param template    prompt template content
     * @param commitMsg   commit message
     * @param description prompt description
     * @return true if publish success
     * @throws NacosException if fail to publish prompt
     */
    default boolean publishPrompt(String namespaceId, String promptKey, String version, String template,
            String commitMsg, String description) throws NacosException {
        return publishPrompt(namespaceId, promptKey, version, template, commitMsg, description, null);
    }
    
    /**
     * Publish prompt with default namespace.
     *
     * @param promptKey prompt key
     * @param version   version
     * @param template  prompt template content
     * @param commitMsg commit message
     * @return true if publish success
     * @throws NacosException if fail to publish prompt
     */
    default boolean publishPrompt(String promptKey, String version, String template, String commitMsg)
            throws NacosException {
        return publishPrompt(Constants.DEFAULT_NAMESPACE_ID, promptKey, version, template, commitMsg, null, null);
    }
    
    /**
     * Delete prompt.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @return true if delete success
     * @throws NacosException if fail to delete prompt
     */
    boolean deletePrompt(String namespaceId, String promptKey) throws NacosException;
    
    /**
     * Delete prompt with default namespace.
     *
     * @param promptKey prompt key
     * @return true if delete success
     * @throws NacosException if fail to delete prompt
     */
    default boolean deletePrompt(String promptKey) throws NacosException {
        return deletePrompt(Constants.DEFAULT_NAMESPACE_ID, promptKey);
    }
    
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
     * List prompt versions page.
     *
     * @param promptKey the prompt key
     * @param pageNo    the page no
     * @param pageSize  the page size
     * @return the page
     * @throws NacosException the nacos exception
     */
    default Page<PromptVersionSummary> listPromptVersions(String promptKey, int pageNo, int pageSize)
            throws NacosException {
        return listPromptVersions(Constants.DEFAULT_NAMESPACE_ID, promptKey, pageNo, pageSize);
    }
    
    /**
     * Update prompt metadata (description and tags).
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param description new description
     * @param bizTags     new biz tags (comma-separated)
     * @return true if update success
     * @throws NacosException if fail to update metadata
     */
    boolean updatePromptMetadata(String namespaceId, String promptKey, String description, String bizTags)
            throws NacosException;
    
    /**
     * Update prompt metadata (description only).
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param description new description
     * @return true if update success
     * @throws NacosException if fail to update metadata
     */
    default boolean updatePromptMetadata(String namespaceId, String promptKey, String description)
            throws NacosException {
        return updatePromptMetadata(namespaceId, promptKey, description, null);
    }
}
