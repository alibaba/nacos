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
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.PromptHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
    
    // ========== Common APIs ==========
    
    public boolean deletePrompt(PromptForm form, String srcUser, String srcIp)
        throws NacosException {
        return promptHandler.deletePrompt(form, srcUser, srcIp);
    }
    
    public Page<PromptMetaSummary> listPrompts(PromptListForm form) throws NacosException {
        return promptHandler.listPrompts(form);
    }
    
    public Page<PromptVersionSummary> listPromptVersions(PromptHistoryForm form)
        throws NacosException {
        return promptHandler.listPromptVersions(form);
    }
    
    // ========== Lifecycle APIs ==========
    
    public PromptMetaInfo getPromptGovernanceDetail(String namespaceId, String promptKey)
        throws NacosException {
        return promptHandler.getPromptGovernanceDetail(namespaceId, promptKey);
    }
    
    public PromptVersionInfo getVersionDetail(String namespaceId, String promptKey, String version)
        throws NacosException {
        return promptHandler.getVersionDetail(namespaceId, promptKey, version);
    }
    
    public PromptVersionInfo downloadPromptVersion(String namespaceId, String promptKey,
        String version)
        throws NacosException {
        return promptHandler.downloadPromptVersion(namespaceId, promptKey, version);
    }
    
    /** Create a prompt draft. */
    public String createDraft(String namespaceId, String promptKey, String basedOnVersion,
        String targetVersion,
        String template, List<PromptVariable> variables, String commitMsg, String description,
        String bizTags)
        throws NacosException {
        return promptHandler.createDraft(namespaceId, promptKey, basedOnVersion, targetVersion,
            template, variables,
            commitMsg, description, bizTags);
    }
    
    public void updateDraft(String namespaceId, String promptKey, String template,
        List<PromptVariable> variables,
        String commitMsg) throws NacosException {
        promptHandler.updateDraft(namespaceId, promptKey, template, variables, commitMsg);
    }
    
    public void deleteDraft(String namespaceId, String promptKey) throws NacosException {
        promptHandler.deleteDraft(namespaceId, promptKey);
    }
    
    public String submit(String namespaceId, String promptKey, String version)
        throws NacosException {
        return promptHandler.submit(namespaceId, promptKey, version);
    }
    
    public void publish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException {
        promptHandler.publish(namespaceId, promptKey, version, updateLatestLabel);
    }
    
    public void forcePublish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException {
        promptHandler.forcePublish(namespaceId, promptKey, version, updateLatestLabel);
    }
    
    public void redraft(String namespaceId, String promptKey, String version)
        throws NacosException {
        promptHandler.redraft(namespaceId, promptKey, version);
    }
    
    public void changeOnlineStatus(String namespaceId, String promptKey, String version,
        boolean online)
        throws NacosException {
        promptHandler.changeOnlineStatus(namespaceId, promptKey, version, online);
    }
    
    public void updateLabels(String namespaceId, String promptKey, Map<String, String> labels)
        throws NacosException {
        promptHandler.updateLabels(namespaceId, promptKey, labels);
    }
    
    public void updateDescription(String namespaceId, String promptKey, String description)
        throws NacosException {
        promptHandler.updateDescription(namespaceId, promptKey, description);
    }
    
    public void updateBizTags(String namespaceId, String promptKey, String bizTags)
        throws NacosException {
        promptHandler.updateBizTags(namespaceId, promptKey, bizTags);
    }
}
