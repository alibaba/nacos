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
import com.alibaba.nacos.ai.form.prompt.PromptLabelBindForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.form.prompt.PromptMetadataForm;
import com.alibaba.nacos.ai.form.prompt.PromptPublishForm;
import com.alibaba.nacos.ai.form.prompt.PromptQueryForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
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
     * Gets prompt meta.
     *
     * @param form the form
     * @return the prompt meta
     * @throws NacosException the nacos exception
     */
    PromptMetaInfo getPromptMeta(PromptForm form) throws NacosException;
    
    /**
     * Query prompt detail prompt version info.
     *
     * @param form the form
     * @return the prompt version info
     * @throws NacosException the nacos exception
     */
    PromptVersionInfo queryPromptDetail(PromptQueryForm form) throws NacosException;
    
    /**
     * Bind prompt label to version.
     *
     * @param form    bind form
     * @param srcUser source user
     * @param srcIp   source ip
     * @return true if bind success
     * @throws NacosException if bind fails
     */
    boolean bindLabel(PromptLabelBindForm form, String srcUser, String srcIp) throws NacosException;
    
    /**
     * Unbind prompt label.
     *
     * @param form    label form
     * @param srcUser source user
     * @param srcIp   source ip
     * @return true if unbind success
     * @throws NacosException if unbind fails
     */
    boolean unbindLabel(PromptLabelForm form, String srcUser, String srcIp) throws NacosException;
    
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
    Page<PromptMetaSummary> listPrompts(PromptListForm form) throws NacosException;
    
    /**
     * List prompt versions page.
     *
     * @param form the form
     * @return the page
     * @throws NacosException the nacos exception
     */
    Page<PromptVersionSummary> listPromptVersions(PromptHistoryForm form) throws NacosException;
    
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
