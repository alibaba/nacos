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

package com.alibaba.nacos.console.proxy.ai;

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
import com.alibaba.nacos.console.handler.ai.PromptHandler;
import org.springframework.stereotype.Component;

/**
 * Prompt proxy for console.
 *
 * @author nacos
 */
@Component
public class PromptProxy {
    
    private final PromptHandler promptHandler;
    
    public PromptProxy(PromptHandler promptHandler) {
        this.promptHandler = promptHandler;
    }
    
    /**
     * Publish a new version of prompt.
     *
     * @param form    prompt publish form
     * @param srcUser source user
     * @param srcIp   source IP
     * @return true if publish success
     * @throws NacosException if publish fails
     */
    public boolean publishPrompt(PromptPublishForm form, String srcUser, String srcIp) throws NacosException {
        return promptHandler.publishPrompt(form, srcUser, srcIp);
    }
    
    public PromptMetaInfo getPromptMeta(PromptForm form) throws NacosException {
        return promptHandler.getPromptMeta(form);
    }
    
    public PromptVersionInfo queryPromptDetail(PromptQueryForm form) throws NacosException {
        return promptHandler.queryPromptDetail(form);
    }
    
    public boolean bindLabel(PromptLabelBindForm form, String srcUser, String srcIp) throws NacosException {
        return promptHandler.bindLabel(form, srcUser, srcIp);
    }
    
    public boolean unbindLabel(PromptLabelForm form, String srcUser, String srcIp) throws NacosException {
        return promptHandler.unbindLabel(form, srcUser, srcIp);
    }
    
    /**
     * Delete prompt.
     *
     * @param form    prompt form
     * @param srcUser source user
     * @param srcIp   source IP
     * @return true if delete success
     * @throws NacosException if delete fails
     */
    public boolean deletePrompt(PromptForm form, String srcUser, String srcIp) throws NacosException {
        return promptHandler.deletePrompt(form, srcUser, srcIp);
    }
    
    /**
     * List prompts with pagination.
     *
     * @param form prompt list form
     * @return prompt list page
     * @throws NacosException if list fails
     */
    public Page<PromptMetaSummary> listPrompts(PromptListForm form) throws NacosException {
        return promptHandler.listPrompts(form);
    }
    
    public Page<PromptVersionSummary> listPromptVersions(PromptHistoryForm form) throws NacosException {
        return promptHandler.listPromptVersions(form);
    }
    
    /**
     * Update prompt metadata (description only).
     *
     * @param form    prompt metadata form
     * @param srcUser source user
     * @param srcIp   source IP
     * @return true if update success
     * @throws NacosException if update fails
     */
    public boolean updatePromptMetadata(PromptMetadataForm form, String srcUser, String srcIp) throws NacosException {
        return promptHandler.updatePromptMetadata(form, srcUser, srcIp);
    }
}
