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

package com.alibaba.nacos.console.handler.impl.remote.ai;

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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.PromptHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Remote implementation of Prompt handler.
 *
 * <p>Calls remote Nacos server through maintainer client for Prompt operations.</p>
 *
 * @author nacos
 */
@Service
@EnabledRemoteHandler
@EnabledAiHandler
public class PromptRemoteHandler implements PromptHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public PromptRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    // ========== Common APIs ==========
    
    @Override
    public boolean deletePrompt(PromptForm form, String srcUser, String srcIp)
        throws NacosException {
        return clientHolder.getAiMaintainerService().prompt().deletePrompt(form.getNamespaceId(),
            form.getPromptKey());
    }
    
    @Override
    public Page<PromptMetaSummary> listPrompts(PromptListForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().prompt().listPrompts(form.getNamespaceId(),
            form.getPromptKey(),
            form.getSearch(), form.getBizTags(), form.getPageNo(), form.getPageSize());
    }
    
    @Override
    public Page<PromptVersionSummary> listPromptVersions(PromptHistoryForm form)
        throws NacosException {
        return clientHolder.getAiMaintainerService().prompt().listPromptVersions(
            form.getNamespaceId(),
            form.getPromptKey(), form.getPageNo(), form.getPageSize());
    }
    
    // ========== Lifecycle APIs ==========
    
    @Override
    public PromptMetaInfo getPromptGovernanceDetail(String namespaceId, String promptKey)
        throws NacosException {
        return clientHolder.getAiMaintainerService().prompt().getPromptGovernanceDetail(namespaceId,
            promptKey);
    }
    
    @Override
    public PromptVersionInfo getVersionDetail(String namespaceId, String promptKey, String version)
        throws NacosException {
        return clientHolder.getAiMaintainerService().prompt().getVersionDetail(namespaceId,
            promptKey, version);
    }
    
    @Override
    public PromptVersionInfo downloadPromptVersion(String namespaceId, String promptKey,
        String version)
        throws NacosException {
        // Remote handler delegates to getVersionDetail; download count is tracked on the target server side.
        return clientHolder.getAiMaintainerService().prompt().getVersionDetail(namespaceId,
            promptKey, version);
    }
    
    @Override
    public String createDraft(String namespaceId, String promptKey, String basedOnVersion,
        String targetVersion,
        String template, List<PromptVariable> variables, String commitMsg, String description,
        String bizTags)
        throws NacosException {
        String variablesJson = variables != null ? JacksonUtils.toJson(variables) : null;
        return clientHolder.getAiMaintainerService().prompt().createDraft(namespaceId, promptKey,
            basedOnVersion,
            targetVersion, template, variablesJson, commitMsg, description, bizTags);
    }
    
    @Override
    public void updateDraft(String namespaceId, String promptKey, String template,
        List<PromptVariable> variables,
        String commitMsg) throws NacosException {
        String variablesJson = variables != null ? JacksonUtils.toJson(variables) : null;
        clientHolder.getAiMaintainerService().prompt().updateDraft(namespaceId, promptKey, template,
            variablesJson,
            commitMsg);
    }
    
    @Override
    public void deleteDraft(String namespaceId, String promptKey) throws NacosException {
        clientHolder.getAiMaintainerService().prompt().deleteDraft(namespaceId, promptKey);
    }
    
    @Override
    public String submit(String namespaceId, String promptKey, String version)
        throws NacosException {
        return clientHolder.getAiMaintainerService().prompt().submit(namespaceId, promptKey,
            version);
    }
    
    @Override
    public void publish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException {
        clientHolder.getAiMaintainerService().prompt().publish(namespaceId, promptKey, version,
            true);
    }
    
    @Override
    public void forcePublish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException {
        clientHolder.getAiMaintainerService().prompt().forcePublish(namespaceId, promptKey, version,
            true);
    }
    
    @Override
    public void redraft(String namespaceId, String promptKey, String version)
        throws NacosException {
        clientHolder.getAiMaintainerService().prompt().redraft(namespaceId, promptKey, version);
    }
    
    @Override
    public void changeOnlineStatus(String namespaceId, String promptKey, String version,
        boolean online)
        throws NacosException {
        clientHolder.getAiMaintainerService().prompt().changeOnlineStatus(namespaceId, promptKey,
            version, online);
    }
    
    @Override
    public void updateLabels(String namespaceId, String promptKey, Map<String, String> labels)
        throws NacosException {
        clientHolder.getAiMaintainerService().prompt().updateLabels(namespaceId, promptKey,
            JacksonUtils.toJson(labels));
    }
    
    @Override
    public void updateDescription(String namespaceId, String promptKey, String description)
        throws NacosException {
        clientHolder.getAiMaintainerService().prompt().updateDescription(namespaceId, promptKey,
            description);
    }
    
    @Override
    public void updateBizTags(String namespaceId, String promptKey, String bizTags)
        throws NacosException {
        clientHolder.getAiMaintainerService().prompt().updateBizTags(namespaceId, promptKey,
            bizTags);
    }
}
