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

package com.alibaba.nacos.console.handler.ai;

import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.form.prompt.PromptMetadataForm;
import com.alibaba.nacos.ai.form.prompt.PromptPublishForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptBasicInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptDetail;
import com.alibaba.nacos.api.ai.model.prompt.PromptHistoryItem;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Prompt handler interface.
 *
 * @author nacos
 */
public interface PromptHandler {
    
    /**
     * Publish a new version of prompt.
     *
     * @param form    prompt publish form
     * @param srcUser source user
     * @param srcIp   source IP
     * @return true if publish success
     * @throws NacosException if publish fails
     */
    boolean publishPrompt(PromptPublishForm form, String srcUser, String srcIp) throws NacosException;
    
    /**
     * Get prompt detail.
     *
     * @param form prompt form
     * @return prompt detail
     * @throws NacosException if get fails
     */
    PromptDetail getPrompt(PromptForm form) throws NacosException;
    
    /**
     * Delete prompt.
     *
     * @param form    prompt form
     * @param srcUser source user
     * @param srcIp   source IP
     * @return true if delete success
     * @throws NacosException if delete fails
     */
    boolean deletePrompt(PromptForm form, String srcUser, String srcIp) throws NacosException;
    
    /**
     * List prompts with pagination.
     *
     * @param form prompt list form
     * @return prompt list page
     * @throws NacosException if list fails
     */
    Page<PromptBasicInfo> listPrompts(PromptListForm form) throws NacosException;
    
    /**
     * List prompt history versions.
     *
     * @param form prompt history form
     * @return history list page
     * @throws NacosException if list fails
     */
    Page<PromptHistoryItem> listPromptHistory(PromptHistoryForm form) throws NacosException;
    
    /**
     * Get prompt history detail.
     *
     * @param form      prompt form
     * @param historyId history record ID
     * @return prompt detail of the history version
     * @throws NacosException if get fails
     */
    PromptDetail getPromptHistoryDetail(PromptForm form, Long historyId) throws NacosException;
    
    /**
     * Update prompt metadata (description only).
     *
     * @param form    prompt metadata form
     * @param srcUser source user
     * @param srcIp   source IP
     * @return true if update success
     * @throws NacosException if update fails
     */
    boolean updatePromptMetadata(PromptMetadataForm form, String srcUser, String srcIp) throws NacosException;
}
